/*
 * OrbisServer is an OSGI web application to expose OGC services.
 *
 * OrbisServer is part of the OrbisGIS platform
 *
 * OrbisGIS is a java GIS application dedicated to research in GIScience.
 * OrbisGIS is developed by the GIS group of the DECIDE team of the
 * Lab-STICC CNRS laboratory, see <http://www.lab-sticc.fr/>.
 *
 * The GIS group of the DECIDE team is located at :
 *
 * Laboratoire Lab-STICC – CNRS UMR 6285
 * Equipe DECIDE
 * UNIVERSITÉ DE BRETAGNE-SUD
 * Institut Universitaire de Technologie de Vannes
 * 8, Rue Montaigne - BP 561 56017 Vannes Cedex
 *
 * OrbisServer is distributed under LGPL 3 license.
 *
 * Copyright (C) 2017 CNRS (Lab-STICC UMR CNRS 6285)
 *
 *
 * OrbisServer is free software: you can redistribute it and/or modify it under the
 * terms of the GNU Lesser General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later
 * version.
 *
 * OrbisServer is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
 * A PARTICULAR PURPOSE. See the GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License along with
 * OrbisServer. If not, see <http://www.gnu.org/licenses/>.
 *
 * For more information, please consult: <http://www.orbisgis.org/>
 * or contact directly:
 * info_at_ orbisgis.org
 */
package org.orbisgis.orbisserver.coreserver.controller;

import net.opengis.ows._2.*;
import net.opengis.wps._2_0.*;
import net.opengis.wps._2_0.GetCapabilitiesType;
import net.opengis.wps._2_0.ObjectFactory;
import org.orbisgis.orbisserver.coreserver.model.*;
import org.orbisgis.orbisserver.coreserver.model.Operation;
import org.orbisgis.orbisserver.coreserver.model.StatusInfo;
import org.orbiswps.scripts.WpsScriptPlugin;
import org.orbiswps.server.WpsServer;
import org.orbiswps.server.WpsServerImpl;
import org.orbiswps.server.model.*;

import javax.sql.DataSource;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;
import java.io.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;

/**
 * Service for the core-server module managing the wps part
 */
public class WpsService implements Service {

    private WpsServer wpsServer;
    private ExecutorService executorService;
    private File workspaceFolder;
    private DataSource ds;
    private List<Operation> cachedOpList;

    public WpsService(DataSource ds, ExecutorService executorService, File workspaceFolder){
        this.ds = ds;
        this.executorService = executorService;
        this.workspaceFolder = workspaceFolder;
        this.cachedOpList = new ArrayList<>();
        createWpsServerInstance();
    }

    @Override
    public StatusInfo executeOperation(ExecuteRequest request) {
        try {
            Map<String, String> outputData = new HashMap<>();
            ExecuteRequestType execute = new ExecuteRequestType();

            for (Map.Entry<String, String> entry : request.getDataMap().entrySet()) {
                DataInputType dataInputType = new DataInputType();
                Data data = new Data();
                data.getContent().add(entry.getValue());
                data.setEncoding("simple");
                data.setMimeType("text/plain");
                dataInputType.setData(data);
                dataInputType.setId(entry.getKey());
                execute.getInput().add(dataInputType);
            }

            for (Map.Entry<String, String> entry : outputData.entrySet()) {
                OutputDefinitionType outputDefinitionType = new OutputDefinitionType();
                outputDefinitionType.setId(entry.getKey());
                outputDefinitionType.setEncoding("simple");
                outputDefinitionType.setMimeType("text/plain");
                outputDefinitionType.setTransmission(DataTransmissionModeType.REFERENCE);
                execute.getOutput().add(outputDefinitionType);
            }


            Unmarshaller unmarshaller = JaxbContainer.JAXBCONTEXT.createUnmarshaller();
            Marshaller marshaller = JaxbContainer.JAXBCONTEXT.createMarshaller();
            ObjectFactory factory = new ObjectFactory();
            //Creates the ExecuteRequestType

            CodeType codeType = new CodeType();
            codeType.setValue(request.getId());
            execute.setIdentifier(codeType);
            execute.setResponse("document");
            execute.setMode("auto");

            //Marshall the ExecuteRequestType object into an OutputStream
            marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, Boolean.TRUE);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            marshaller.marshal(factory.createExecute(execute), out);
            //Write the OutputStream content into an Input stream before sending it to the wpsService
            InputStream in = new DataInputStream(new ByteArrayInputStream(out.toByteArray()));
            ByteArrayOutputStream xml = (ByteArrayOutputStream) wpsServer.callOperation(in);
            //Get back the result of the DescribeProcess request as a BufferReader
            InputStream resultXml = new ByteArrayInputStream(xml.toByteArray());
            //Unmarshall the result and check that the object is the same as the resource unmashalled xml.
            Object resultObject = unmarshaller.unmarshal(resultXml);

            net.opengis.wps._2_0.StatusInfo statusInfo = (net.opengis.wps._2_0.StatusInfo) resultObject;
            StatusInfo status = new StatusInfo(statusInfo.getJobID());
            status.setStatus(statusInfo.getStatus());
            if (statusInfo.getPercentCompleted() != null) {
                status.setPercentCompleted(statusInfo.getPercentCompleted());
            }
            if (statusInfo.getNextPoll() != null) {
                status.setNextPoll(statusInfo.getNextPoll());
            }
            if (statusInfo.getEstimatedCompletion() != null) {
                status.setEstimatedCompletion(statusInfo.getEstimatedCompletion());
            }
            return status;
        }
        catch(Exception e){

        }
        return null;
    }

    @Override
    public StatusInfo getStatus(StatusRequest request) {

        try {
            Unmarshaller unmarshaller = JaxbContainer.JAXBCONTEXT.createUnmarshaller();
            Marshaller marshaller = JaxbContainer.JAXBCONTEXT.createMarshaller();
            //Get the corresponding GetStatus
            GetStatus getStatus = new GetStatus();
            getStatus.setJobID(request.getId());
            //Marshall the GetStatus object into an OutputStream
            marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, Boolean.TRUE);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            marshaller.marshal(getStatus, out);
            //Write the OutputStream content into an Input stream before sending it to the wpsService
            InputStream in = new DataInputStream(new ByteArrayInputStream(out.toByteArray()));
            ByteArrayOutputStream xml = (ByteArrayOutputStream) wpsServer.callOperation(in);
            //Get back the result of the DescribeProcess request as a BufferReader
            InputStream resultXml = new ByteArrayInputStream(xml.toByteArray());
            //Unmarshall the result and check that the object is the same as the resource unmashalled xml.
            net.opengis.wps._2_0.StatusInfo info = (net.opengis.wps._2_0.StatusInfo)unmarshaller.unmarshal(resultXml);
            StatusInfo statusInfo = new StatusInfo(info.getJobID());
            statusInfo.setEstimatedCompletion(info.getEstimatedCompletion());
            statusInfo.setNextPoll(info.getNextPoll());
            if(info.getNextPoll() != null) {
                statusInfo.setNextRefreshMillis(info.getNextPoll().toGregorianCalendar().getTime().getTime());
            }
            else{
                statusInfo.setNextRefreshMillis(-1);
            }
            statusInfo.setPercentCompleted(info.getPercentCompleted());
            statusInfo.setProcessTitle(request.getProcessTitle());
            statusInfo.setProcessID(request.getProcessId());
            statusInfo.setStatus(info.getStatus());
            return statusInfo;
        }
        catch(Exception e){

        }
        return null;
    }

    @Override
    public List<Operation> getAllOperation() {
        cachedOpList = new ArrayList<>();
        try {
            Unmarshaller unmarshaller = JaxbContainer.JAXBCONTEXT.createUnmarshaller();
            Marshaller marshaller = JaxbContainer.JAXBCONTEXT.createMarshaller();
            ObjectFactory factory = new ObjectFactory();
            //Creates the getCapabilities
            GetCapabilitiesType getCapabilitiesType = new GetCapabilitiesType();
            GetCapabilitiesType.AcceptLanguages acceptLanguages = new GetCapabilitiesType.AcceptLanguages();
            acceptLanguages.getLanguage().add("*");
            getCapabilitiesType.setAcceptLanguages(acceptLanguages);
            AcceptVersionsType acceptVersionsType = new AcceptVersionsType();
            acceptVersionsType.getVersion().add("2.0.0");
            getCapabilitiesType.setAcceptVersions(acceptVersionsType);
            SectionsType sectionsType = new SectionsType();
            sectionsType.getSection().add("All");
            getCapabilitiesType.setSections(sectionsType);
            //Marshall the DescribeProcess object into an OutputStream
            marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, Boolean.TRUE);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            marshaller.marshal(factory.createGetCapabilities(getCapabilitiesType), out);
            //Write the OutputStream content into an Input stream before sending it to the wpsService
            InputStream in = new DataInputStream(new ByteArrayInputStream(out.toByteArray()));
            ByteArrayOutputStream xml = (ByteArrayOutputStream) wpsServer.callOperation(in);
            //Get back the result of the DescribeProcess request as a BufferReader
            ByteArrayInputStream resultXml = new ByteArrayInputStream(xml.toByteArray());
            //Unmarshall the result and check that the object is the same as the resource unmashalled xml.
            Object resultObject = unmarshaller.unmarshal(resultXml);
            WPSCapabilitiesType wpsCapabilitiesType = (WPSCapabilitiesType) ((JAXBElement) resultObject).getValue();

            List<KeywordsType> keywordsType = null;
            ArrayList<String> listWordEnglish = new ArrayList<>();

            for(ProcessSummaryType process : wpsCapabilitiesType.getContents().getProcessSummary()){
                Operation op = new Operation(process.getTitle().get(0).getValue(), process.getIdentifier().getValue());
                if(process.getAbstract()!=null && !process.getAbstract().isEmpty()) {
                    op.setAbstr(process.getAbstract().get(0).getValue());
                    keywordsType = process.getKeywords();
                    for(KeywordsType keyword : keywordsType){
                        for (LanguageStringType language : keyword.getKeyword()) {
                            if(language.getLang().equals("en")) {
                                op.getKeyWord().add(language.getValue());
                            }
                        }
                    }
                }
                cachedOpList.add(op);
            }
        }
        catch (Exception e){
        }
        return cachedOpList;
    }

    @Override
    public boolean hasOperation(String id) {
        for(Operation op : cachedOpList){
            if(op.getId().equals(id)){
                return true;
            }
        }
        return false;
    }

    @Override
    public Operation getOperation(String id) {
        Operation operation = null;
        for(Operation op : cachedOpList){
            if(op.getId().equals(id)){
                operation = new Operation(op.getTitle(), op.getId());
                operation.setAbstr(op.getAbstr());
                try {
                    Unmarshaller unmarshaller = JaxbContainer.JAXBCONTEXT.createUnmarshaller();
                    Marshaller marshaller = JaxbContainer.JAXBCONTEXT.createMarshaller();
                    //Creates the DescribeProcess
                    DescribeProcess describeProcess = new DescribeProcess();
                    describeProcess.setLang("en");
                    CodeType codeType = new CodeType();
                    codeType.setValue(id);
                    describeProcess.getIdentifier().add(codeType);
                    //Marshall the DescribeProcess object into an OutputStream
                    marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, Boolean.TRUE);
                    ByteArrayOutputStream out = new ByteArrayOutputStream();
                    marshaller.marshal(describeProcess, out);
                    //Write the OutputStream content into an Input stream before sending it to the wpsService
                    InputStream in = new DataInputStream(new ByteArrayInputStream(out.toByteArray()));
                    ByteArrayOutputStream xml = (ByteArrayOutputStream) wpsServer.callOperation(in);
                    //Get back the result of the DescribeProcess request as a BufferReader
                    InputStream resultXml = new ByteArrayInputStream(xml.toByteArray());
                    //Unmarshall the result and check that the object is the same as the resource unmashalled xml.
                    Object resultObject = unmarshaller.unmarshal(resultXml);
                    if(resultObject instanceof ProcessOfferings) {
                        ProcessOfferings processOfferings = (ProcessOfferings)resultObject;
                        if(processOfferings.getProcessOffering() != null && !processOfferings.getProcessOffering().isEmpty()){
                            for(InputDescriptionType idt : processOfferings.getProcessOffering().get(0).getProcess().getInput()){
                                String title = idt.getTitle().get(0).getValue();
                                String name = idt.getDataDescription().getValue().getClass().getSimpleName();
                                String type = null;
                                Boolean optional = (idt.getMinOccurs().intValue()==0);
                                Map<String, Object> attributeMap = new HashMap<>();
                                DataDescriptionType dataDescriptionType = idt.getDataDescription().getValue();
                                if(dataDescriptionType instanceof LiteralDataType){
                                    if(name.equalsIgnoreCase("LiteralDataType")) {
                                        LiteralDataType literalData = (LiteralDataType) idt.getDataDescription().getValue();
                                        for (LiteralDataType.LiteralDataDomain ldd : literalData.getLiteralDataDomain()) {
                                            if (ldd.isDefault()) {
                                                String dataType = ldd.getDataType().getValue();
                                                if (dataType.equalsIgnoreCase("string")) {
                                                    type = "string";
                                                }
                                                if (dataType.equalsIgnoreCase("boolean")) {
                                                    type = "boolean";
                                                    attributeMap.put("value", "false");
                                                    if (ldd.isSetDefaultValue()) {
                                                        attributeMap.put("value", ldd.getDefaultValue().getValue());
                                                    }
                                                }
                                                if (dataType.equalsIgnoreCase("double") || dataType.equalsIgnoreCase("integer") ||
                                                        dataType.equalsIgnoreCase("float") || dataType.equalsIgnoreCase("short") ||
                                                        dataType.equalsIgnoreCase("byte") || dataType.equalsIgnoreCase("unsigned_byte") ||
                                                        dataType.equalsIgnoreCase("long")) {
                                                    if (dataType.equalsIgnoreCase("double") || dataType.equalsIgnoreCase("float")) {
                                                        attributeMap.put("spacing", "0.1");
                                                    }
                                                    type = "number";
                                                    if (ldd.isSetDefaultValue()) {
                                                        attributeMap.put("value", ldd.getDefaultValue().getValue());
                                                    }
                                                    if (ldd.isSetAllowedValues()) {
                                                        for (Object valueOrRange : ldd.getAllowedValues().getValueOrRange()) {
                                                            if (valueOrRange instanceof ValueType) {
                                                                ValueType value = (ValueType) valueOrRange;
                                                                attributeMap.put("value", value.getValue());
                                                            }
                                                            if (valueOrRange instanceof RangeType) {
                                                                RangeType range = (RangeType) valueOrRange;
                                                                attributeMap.put("min", range.getMinimumValue().getValue());
                                                                attributeMap.put("max", range.getMaximumValue().getValue());
                                                                attributeMap.put("spacing", range.getSpacing().getValue());
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                                if(dataDescriptionType instanceof JDBCTable){
                                    attributeMap.put("value", "Table name");
                                    JDBCTable table = (JDBCTable)dataDescriptionType;
                                    if(table.getDefaultValue() != null && !table.getDefaultValue().isEmpty()){
                                        attributeMap.put("value", table.getDefaultValue());
                                    }
                                }
                                if(dataDescriptionType instanceof JDBCColumn){
                                    attributeMap.put("value", "Columns name");
                                    JDBCColumn column = (JDBCColumn)dataDescriptionType;
                                    if(column.getDefaultValues() != null && column.getDefaultValues().length>0){
                                        StringBuilder str = new StringBuilder();
                                        for(String val : column.getDefaultValues()){
                                            if(str.length() > 0){
                                                str.append(",");
                                            }
                                            str.append(val);
                                        }
                                        attributeMap.put("value", str.toString());
                                    }
                                }
                                if(dataDescriptionType instanceof JDBCValue){
                                    attributeMap.put("value", "Values name");
                                    JDBCValue value = (JDBCValue)dataDescriptionType;
                                    if(value.getDefaultValues() != null && value.getDefaultValues().length>0){
                                        StringBuilder str = new StringBuilder();
                                        for(String val : value.getDefaultValues()){
                                            if(str.length() > 0){
                                                str.append(",");
                                            }
                                            str.append(val);
                                        }
                                        attributeMap.put("value", str.toString());
                                    }
                                }
                                if(dataDescriptionType instanceof Enumeration){
                                    Enumeration enumeration = (Enumeration)dataDescriptionType;
                                    attributeMap.put("multiSelection", enumeration.isMultiSelection());
                                    attributeMap.put("valueList", enumeration.getValues());
                                }
                                String identifier = idt.getIdentifier().getValue();
                                Input input = new Input(title, name, identifier, type, attributeMap, optional);
                                operation.addInput(input);
                            }
                        }
                    }
                }
                catch(Exception e){
                }
            }
        }
        return operation;
    }

    /**
     * Creates an  instance of the WpsServer.
     */
    private void createWpsServerInstance(){
        wpsServer = new WpsServerImpl(workspaceFolder.getAbsolutePath(), ds);
        wpsServer.setExecutorService(executorService);
        wpsServer.setDatabase(WpsServer.Database.H2GIS);
        wpsServer.setDataSource(ds);

        WpsScriptPlugin scriptPlugin = new WpsScriptPlugin();
        scriptPlugin.setWpsServer(wpsServer);
        scriptPlugin.activate();
    }
}
