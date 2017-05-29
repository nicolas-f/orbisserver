/**
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
package org.orbisgis.orbisserver.manager;

import net.opengis.ows._2.AcceptVersionsType;
import net.opengis.ows._2.CodeType;
import net.opengis.ows._2.SectionsType;
import net.opengis.wps._1_0_0.GetCapabilities;
import net.opengis.wps._2_0.*;
import org.apache.felix.ipojo.annotations.Requires;
import org.orbiswps.scripts.WpsScriptPlugin;
import org.orbiswps.server.WpsServer;
import org.orbiswps.server.WpsServerImpl;
import org.orbiswps.server.model.JaxbContainer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.xnap.commons.i18n.I18n;
import org.xnap.commons.i18n.I18nFactory;

import javax.sql.DataSource;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Class managing the WpsServer instances.
 *
 * @author Sylvain PALOMINOS
 * @author Guillaume Mande
 */
public class WpsServerManager{

    /** Logger */
    private static final Logger LOGGER = LoggerFactory.getLogger(WpsServerImpl.class);

    /**
     * Data source used by the WpsServer.
     */
    @Requires
    private static DataSource ds;

    /**
     * Instance of the WpsServer.
     */
    private static WpsServer wpsServer;

    /** List of all identifier of all the processes */
    private List<CodeType> codeTypeList;

    /**
     * Returns the instance of the WpsServer. If it was not already created, create it.
     * @return The instance of the WpsServer
     */
    public static WpsServer getWpsServer(){
        if(wpsServer == null){
            createWpsServerInstance();
        }
        return wpsServer;
    }

    /**
     * Creates an  instance of the WpsServer.
     */
    private static void createWpsServerInstance(){
        wpsServer = new WpsServerImpl(System.getProperty("java.io.tmpdir"), ds);
        WpsScriptPlugin scriptPlugin = new WpsScriptPlugin();
        scriptPlugin.setWpsServer(wpsServer);
        scriptPlugin.activate();
    }

    /**
     * Method to get the xml file corresponding to the GetCapabilities request.
     *
     * @throws JAXBException JAXB Exception.
     * @Return The processes list into a String.
     */
    public String getListFromGetCapabilities() throws JAXBException {
        String processesList = "";

        this.codeTypeList = new ArrayList<CodeType>();

        List<ProcessSummaryType> list = getXMLFromGetCapabilities().getContents().getProcessSummary();
        for (ProcessSummaryType processSummaryType : list) {
            processesList = processesList + processSummaryType.getTitle().get(0).getValue() + "\n";
            this.codeTypeList.add(processSummaryType.getIdentifier());
        }
        return processesList;
    }

    /**
     * Return the wpsCapabilitiesType object which is a xml object.
     *
     * @throws JAXBException JAXB Exception.
     * @Return the wpsCapabilitiesType object.
     */
    public WPSCapabilitiesType getXMLFromGetCapabilities() throws JAXBException {
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
        ByteArrayOutputStream xml = (ByteArrayOutputStream) this.getWpsServer().callOperation(in);
        //Get back the result of the DescribeProcess request as a BufferReader
        ByteArrayInputStream resultXml = new ByteArrayInputStream(xml.toByteArray());
        //Unmarshall the result and check that the object is the same as the resource unmashalled xml.
        Object resultObject = unmarshaller.unmarshal(resultXml);
        WPSCapabilitiesType wpsCapabilitiesType = (WPSCapabilitiesType) ((JAXBElement) resultObject).getValue();

        return wpsCapabilitiesType;
    }

    /**
     * Return the xml file corresponding to the DescribeProcess request.
     *
     * @throws JAXBException JAXB Exception.
     * @Return a ProcessOfferings object
     */
    public Object getXMLFromDescribeProcess(String id) throws JAXBException {
        getListFromGetCapabilities();
        Unmarshaller unmarshaller = JaxbContainer.JAXBCONTEXT.createUnmarshaller();
        Marshaller marshaller = JaxbContainer.JAXBCONTEXT.createMarshaller();
        //Creates the getCapabilities
        CodeType codeTypeFinal = new CodeType();
        for(CodeType codeType : codeTypeList){
            if(codeType.getValue().equals(id)){
                codeTypeFinal = codeType;
            }
        }
        DescribeProcess describeProcess = new DescribeProcess();
        describeProcess.setLang("en");
        describeProcess.getIdentifier().add(codeTypeFinal);
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

        return resultObject;
    }


    /**
     * Return the Document object which is a xml object.
     *
     * @throws JAXBException JAXB Exception.
     * @Return the wpsCapabilitiesType object.
     */
    public Document getDocumentFromGetCapabilities100() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            Marshaller marshaller = JaxbContainer.JAXBCONTEXT.createMarshaller();
            //Creates the getCapabilities
            GetCapabilities getCapabilities = new GetCapabilities();
            getCapabilities.setLanguage("en");
            net.opengis.ows._1.AcceptVersionsType versionsType = new net.opengis.ows._1.AcceptVersionsType();
            versionsType.getVersion().add("1.0.0");
            getCapabilities.setAcceptVersions(versionsType);
            //Marshall the DescribeProcess object into an OutputStream
            marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, Boolean.TRUE);
            marshaller.marshal(getCapabilities, out);
        } catch (Exception e) {
            LOGGER.error("Unable to create the WPS request.");
        }
        //Write the OutputStream content into an Input stream before sending it to the wpsService
        InputStream in = new DataInputStream(new ByteArrayInputStream(out.toByteArray()));
        ByteArrayOutputStream xml = (ByteArrayOutputStream) getWpsServer().callOperation(in);
        //Get back the result of the DescribeProcess request as a BufferReader
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder;
        try {
            builder = factory.newDocumentBuilder();
            ByteArrayInputStream stream = new ByteArrayInputStream(xml.toByteArray());
            return builder.parse(stream);
        } catch (Exception e) {
            LOGGER.error("Unable to convert the WPSServer answer into a Document Object.");
        }

        return null;
    }

    /**
     * Return the Document object corresponding to the DescribeProcess request.
     *
     * @throws JAXBException JAXB Exception.
     * @Return a ProcessOfferings object
     */
    public Document getDocumentFromDescribeProcess100(String id) throws JAXBException {
        Marshaller marshaller = JaxbContainer.JAXBCONTEXT.createMarshaller();
        net.opengis.wps._1_0_0.DescribeProcess describeProcess = new net.opengis.wps._1_0_0.DescribeProcess();
        describeProcess.setLanguage("en");
        net.opengis.ows._1.CodeType codeType = new net.opengis.ows._1.CodeType();
        codeType.setValue(id);
        describeProcess.getIdentifier().add(codeType);
        //Marshall the DescribeProcess object into an OutputStream
        marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, Boolean.TRUE);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        marshaller.marshal(describeProcess, out);
        //Write the OutputStream content into an Input stream before sending it to the wpsService
        InputStream in = new DataInputStream(new ByteArrayInputStream(out.toByteArray()));
        ByteArrayOutputStream xml = (ByteArrayOutputStream) getWpsServer().callOperation(in);
        //Get back the result of the DescribeProcess request as a BufferReader
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder;
        try {
            builder = factory.newDocumentBuilder();
            ByteArrayInputStream stream = new ByteArrayInputStream(xml.toByteArray());
            return builder.parse(stream);
        } catch (Exception e) {
            LOGGER.error("Unable to convert the WPSServer answer into a Document Object.");
        }

        return null;
    }

    /**
     * This method returns the list of identifiers from the CodeType's list.
     *
     * @throws JAXBException
     * @return List of String
     */
    public List<String> getCodeTypeList() throws JAXBException {
        getListFromGetCapabilities();
        List<String> listId = new ArrayList<String>();

        for(CodeType codeType : this.codeTypeList){
            listId.add(codeType.getValue());
        }
        return listId;
    }
}
