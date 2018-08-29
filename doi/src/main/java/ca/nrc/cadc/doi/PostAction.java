/*
************************************************************************
*******************  CANADIAN ASTRONOMY DATA CENTRE  *******************
**************  CENTRE CANADIEN DE DONNÉES ASTRONOMIQUES  **************
*
*  (c) 2018.                            (c) 2018.
*  Government of Canada                 Gouvernement du Canada
*  National Research Council            Conseil national de recherches
*  Ottawa, Canada, K1A 0R6              Ottawa, Canada, K1A 0R6
*  All rights reserved                  Tous droits réservés
*
*  NRC disclaims any warranties,        Le CNRC dénie toute garantie
*  expressed, implied, or               énoncée, implicite ou légale,
*  statutory, of any kind with          de quelque nature que ce
*  respect to the software,             soit, concernant le logiciel,
*  including without limitation         y compris sans restriction
*  any warranty of merchantability      toute garantie de valeur
*  or fitness for a particular          marchande ou de pertinence
*  purpose. NRC shall not be            pour un usage particulier.
*  liable in any event for any          Le CNRC ne pourra en aucun cas
*  damages, whether direct or           être tenu responsable de tout
*  indirect, special or general,        dommage, direct ou indirect,
*  consequential or incidental,         particulier ou général,
*  arising from the use of the          accessoire ou fortuit, résultant
*  software.  Neither the name          de l'utilisation du logiciel. Ni
*  of the National Research             le nom du Conseil National de
*  Council of Canada nor the            Recherches du Canada ni les noms
*  names of its contributors may        de ses  participants ne peuvent
*  be used to endorse or promote        être utilisés pour approuver ou
*  products derived from this           promouvoir les produits dérivés
*  software without specific prior      de ce logiciel sans autorisation
*  written permission.                  préalable et particulière
*                                       par écrit.
*
*  This file is part of the             Ce fichier fait partie du projet
*  OpenCADC project.                    OpenCADC.
*
*  OpenCADC is free software:           OpenCADC est un logiciel libre ;
*  you can redistribute it and/or       vous pouvez le redistribuer ou le
*  modify it under the terms of         modifier suivant les termes de
*  the GNU Affero General Public        la “GNU Affero General Public
*  License as published by the          License” telle que publiée
*  Free Software Foundation,            par la Free Software Foundation
*  either version 3 of the              : soit la version 3 de cette
*  License, or (at your option)         licence, soit (à votre gré)
*  any later version.                   toute version ultérieure.
*
*  OpenCADC is distributed in the       OpenCADC est distribué
*  hope that it will be useful,         dans l’espoir qu’il vous
*  but WITHOUT ANY WARRANTY;            sera utile, mais SANS AUCUNE
*  without even the implied             GARANTIE : sans même la garantie
*  warranty of MERCHANTABILITY          implicite de COMMERCIALISABILITÉ
*  or FITNESS FOR A PARTICULAR          ni d’ADÉQUATION À UN OBJECTIF
*  PURPOSE.  See the GNU Affero         PARTICULIER. Consultez la Licence
*  General Public License for           Générale Publique GNU Affero
*  more details.                        pour plus de détails.
*
*  You should have received             Vous devriez avoir reçu une
*  a copy of the GNU Affero             copie de la Licence Générale
*  General Public License along         Publique GNU Affero avec
*  with OpenCADC.  If not, see          OpenCADC ; si ce n’est
*  <http://www.gnu.org/licenses/>.      pas le cas, consultez :
*                                       <http://www.gnu.org/licenses/>.
*
************************************************************************
*/

package ca.nrc.cadc.doi;

import ca.nrc.cadc.auth.AuthenticationUtil;
import ca.nrc.cadc.auth.HttpPrincipal;
import ca.nrc.cadc.auth.SSLUtil;
import ca.nrc.cadc.cred.client.CredUtil;
import ca.nrc.cadc.net.HttpDownload;
import ca.nrc.cadc.util.MultiValuedProperties;
import ca.nrc.cadc.util.PropertiesReader;
import ca.nrc.cadc.util.StringUtil;
import ca.nrc.cadc.vos.ContainerNode;
import ca.nrc.cadc.vos.DataNode;
import ca.nrc.cadc.vos.Direction;
import ca.nrc.cadc.vos.Node;
import ca.nrc.cadc.vos.NodeProperty;
import ca.nrc.cadc.vos.NodeWriter;
import ca.nrc.cadc.vos.Protocol;
import ca.nrc.cadc.vos.Transfer;
import ca.nrc.cadc.vos.VOS;
import ca.nrc.cadc.vos.VOSURI;
import ca.nrc.cadc.vos.client.ClientTransfer;
import ca.nrc.cadc.vos.client.VOSpaceClient;
import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import javax.security.auth.Subject;
import org.apache.log4j.Logger;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.Namespace;

/**
 *
 */
public class PostAction extends DOIAction {

    private static final Logger log = Logger.getLogger(PostAction.class);

//    private static final String ILLEGAL_NAME_CHARS = "[~#@*+%{}<>\\[\\]|\"\\_^- \n\r\t]";

    private static final String AUTHOR_PARAM = "author";
    private static final String TITLE_PARAM = "title";
    private static final String CADC_DOI_PREFIX = "10.11570";
    private static final String CADC_CISTI_PREFIX = "CISTI_CADC_";

    private VOSURI target;
    private List<NodeProperty> properties;

    public PostAction() {
        super();
    }

    @Override
    public void doActionImpl() throws Exception {

        Subject subject = AuthenticationUtil.getCurrentSubject();

        // DOINum is parsed out in DOIAction.initRequest()
        if (DOINumInputStr == null) {
            requestType = CREATE_REQUEST;

            // Determine next DOI number
            Node baseNode = vosClient.getNode(astroDataURI.getPath());
            String nextDoiSuffix = generateNextDOINumber((ContainerNode)baseNode);
            log.info("Next DOI suffix is: " + nextDoiSuffix);

            // Update DOI xml with DOI number
            Element identifier = doiDocRoot.getChild("identifier", doiNamespace);
            identifier.setText(CADC_DOI_PREFIX + "/" + nextDoiSuffix);

            // Create containing folder
            String folderName = DOI_BASE_VOSPACE + "/" + nextDoiSuffix;
            target = new VOSURI(new URI(folderName));
            // Need to include properties later?
            Node newFolder = new ContainerNode(target);
            vosClient.createNode(newFolder);

            // Create 'data' folder under containing folder.
            // This is where the calling user will upload their DOI data
            // TODO: set permissions on this folder, including group where
            // calling user is the admin
            String dataFolderName = folderName + "/data";
            target = new VOSURI(new URI(dataFolderName));
            Node newDataFolder = new ContainerNode(target);
            vosClient.createNode(newDataFolder);

            // Create VOSpace data node to house XML doc using doi filename
            String nextDoiFilename = getNextDOIFilename(nextDoiSuffix);
            log.debug("next doi filename: " + nextDoiFilename);

            String doiFilename = folderName + "/" + nextDoiFilename;
            target = new VOSURI(new URI(doiFilename));
            Node doiFileDataNode = new DataNode(target);
            vosClient.createNode(doiFileDataNode);

            writeDoiDocToVospace(doiFilename);

            // TODO: apply permissions to folder using vospace group & calling user as node attribute

            // output document to syncOutput
            writeDoiDocToSyncOutput();
//            StringBuilder doiXmlString = new StringBuilder();
//            DoiXmlWriter writer = new DoiXmlWriter();
//            writer.write(doiDocument,doiXmlString);
//            syncOutput.getOutputStream().write(doiXmlString.toString().getBytes());

        }
        else {
            throw new UnsupportedOperationException("Editing DOI Metadata not supported.");
            // validate DOI supplied
            // determine if user has access to this DOI
            // validate metadata supplied

        }


    }


    /*
     * Confirm that required information is provided
     * TODO: as been replaced by DoiInlineContentHandler class to parse input streams
     * Q: will key-value pairs be accepted ever?
     */
    private void validateDOIMetadata() {
        // replace 'syncInput' with DOIMetadata object that reader/writer will create
        String author = syncInput.getParameter(AUTHOR_PARAM);

        if (!StringUtil.hasText(author)) {
            throw new IllegalArgumentException("Author is required");
        }
        if (!author.matches("[A-Za-z0-9\\-]+")) {
            throw new IllegalArgumentException("Author can only contain alpha-numeric chars and '-'");
        }

        String title = syncInput.getParameter(TITLE_PARAM);

        if (!StringUtil.hasText(title)) {
            throw new IllegalArgumentException("Title is required");
        }

        // Required:
        // author
        // title

        // At this point the DOIMetadata pojo will be populated, once code for XMLReader/Writer is merged
        // into here.
        // Q: will this validate the DOIMetadata object after it's read? Might be most efficient...
    }




    /*
     * Generate next DOI, format: YY.####
     */
    private String getNextDOISuffix() {
        // Check VOSpace folder names under AstroDaaCititationDOI, get the 'largest' of the current year
        // 'YY' is a 2 digit year
        DateFormat df = new SimpleDateFormat("yy"); // Just the year, with 2 digits
        String formattedDate = df.format(Calendar.getInstance().getTime());
        return formattedDate + ".####";
    }

    private String generateNextDOINumber(ContainerNode baseNode) {

        // child nodes of baseNode should have structure YY.XXXX
        // go through list of child nodes
        // extract XXXX
        // track largest
        // add 1
        // reconstruct YY.XXXX structure and return

        // Look into the node list for folders from current year only
        DateFormat df = new SimpleDateFormat("yy"); // Just the year, with 2 digits
        String currentYear = df.format(Calendar.getInstance().getTime());

        String nextDOI = "";
        Integer maxDoi = 0;
        if (baseNode.getNodes().size() > 0) {
            for( Node childNode : baseNode.getNodes()) {
                String[] nameParts = childNode.getName().split("\\.");
                if (nameParts[0].equals(currentYear)) {
                    int curDoiNum = Integer.parseInt(nameParts[1]);
                    if (curDoiNum > maxDoi) {
                        maxDoi = curDoiNum;
                    }
                }
            }
        }

        maxDoi++;
        String formattedDOI = String.format("%04d", maxDoi);
        return currentYear + "." + formattedDOI;
    }

    private String getNextDOIFilename(String suffix) {
        return CADC_CISTI_PREFIX + suffix + ".xml";
    }

}
