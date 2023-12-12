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

import ca.nrc.cadc.ac.ACIdentityManager;
import ca.nrc.cadc.auth.AuthenticationUtil;
import ca.nrc.cadc.doi.datacite.DoiParsingException;
import ca.nrc.cadc.doi.datacite.DoiXmlReader;
import ca.nrc.cadc.doi.datacite.Resource;
import ca.nrc.cadc.net.InputStreamWrapper;
import ca.nrc.cadc.net.ResourceNotFoundException;
import ca.nrc.cadc.util.StringUtil;
import ca.nrc.cadc.vos.ContainerNode;
import ca.nrc.cadc.vos.Direction;
import ca.nrc.cadc.vos.DataNode;
import ca.nrc.cadc.vos.Node;
import ca.nrc.cadc.vos.NodeNotFoundException;
import ca.nrc.cadc.vos.Protocol;
import ca.nrc.cadc.vos.Transfer;
import ca.nrc.cadc.vos.VOS;
import ca.nrc.cadc.vos.VOSURI;
import ca.nrc.cadc.vos.client.ClientTransfer;
import ca.nrc.cadc.vos.client.VOSpaceClient;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.AccessControlException;
import java.util.Set;
import javax.security.auth.Subject;
import javax.security.auth.x500.X500Principal;
import org.apache.log4j.Logger;

public class VospaceDoiClient {

    private static final Logger log = Logger.getLogger(VospaceDoiClient.class);
    protected static final String DOI_BASE_FILEPATH = "/AstroDataCitationDOI/CISTI.CANFAR";
    protected static final String DOI_BASE_VOSPACE = "vos://cadc.nrc.ca!vault" + DOI_BASE_FILEPATH;
    protected static final String DOI_VOS_REQUESTER_PROP = "ivo://cadc.nrc.ca/vospace/doi#requester";
    private static final X500Principal DOIADMIN = new X500Principal("C=ca,O=hia,OU=cadc,CN=doiadmin_045");

    private final Long callersNumericId;
    private VOSpaceClient vosClient = null;
    private VOSURI baseDataURI = null;
    private String xmlFilename = "";
    private boolean includePublicNodes = false;

    public VospaceDoiClient(Subject callingSubject, Boolean includePublicNodes) throws URISyntaxException {
        this.baseDataURI = new VOSURI(new URI(DOI_BASE_VOSPACE));
        this.vosClient = new VOSpaceClient(baseDataURI.getServiceURI());

        ACIdentityManager acIdentMgr = new ACIdentityManager();
        this.callersNumericId = (Long) acIdentMgr.toOwner(callingSubject);
        if (includePublicNodes != null) {
            this.includePublicNodes = includePublicNodes;
        }
    }

    public VOSpaceClient getVOSpaceClient() {
        return this.vosClient;
    }

    public VOSURI getDoiBaseVOSURI() {
        return this.baseDataURI;
    }

    public ContainerNode getContainerNode(String path) throws NodeNotFoundException, AccessControlException {
        String nodePath = baseDataURI.getPath();
        if (StringUtil.hasText(path)) {
            nodePath = nodePath + "/" + path;
        }
        ContainerNode requestedNode = null;

        try {
            requestedNode = (ContainerNode) vosClient.getNode(nodePath);
        } catch (NodeNotFoundException | AccessControlException ef) {
            throw ef;
        } catch (Exception e) {
            throw new RuntimeException(e.getMessage());
        }

        return requestedNode;
    }

    public DataNode getDataNode(String path) throws NodeNotFoundException, AccessControlException {
        String nodePath = baseDataURI.getPath();
        if (StringUtil.hasText(path)) {
            nodePath = nodePath + "/" + path;
        }
        DataNode requestedNode = null;

        try {
            requestedNode =  (DataNode) vosClient.getNode(nodePath);
        } catch (NodeNotFoundException | AccessControlException ef) {
            throw ef;
        } catch (Exception e) {
            throw new RuntimeException(e.getMessage());
        }

        return requestedNode;
    }

    public Resource getResource(String doiSuffixString, String doiFilename) throws Exception {
        VOSURI docDataURI = new VOSURI(baseDataURI.toString() + "/" + doiSuffixString + "/" + doiFilename);

        return getDoiDocFromVOSpace(docDataURI);
    }

    //  doi admin should have access as well
    public boolean isCallerAllowed(Node node) {
        boolean isRequesterNode = false;

        if (this.includePublicNodes && isPublicNode(node)) {
            isRequesterNode = true;
        } else {
            String requester = node.getPropertyValue(DOI_VOS_REQUESTER_PROP);
            log.debug("requester for node: " + requester);
            if (StringUtil.hasText(requester)) {
                isRequesterNode = requester.equals(this.callersNumericId.toString());
                Set<X500Principal> xset = AuthenticationUtil.getCurrentSubject().getPrincipals(X500Principal.class);
                for (X500Principal p : xset) {
                    isRequesterNode = isRequesterNode || AuthenticationUtil.equals(p, DOIADMIN);
                }
            }
        }
        return isRequesterNode;
    }

    public boolean isPublicNode(Node node) {
        return node.getPropertyValue(VOS.PROPERTY_URI_ISPUBLIC).equals("true");
    }

    private Resource getDoiDocFromVOSpace(VOSURI dataNode) throws Exception {

        Transfer transfer = new Transfer(dataNode.getURI(), Direction.pullFromVoSpace);
        Protocol put = new Protocol(VOS.PROTOCOL_HTTPS_GET);
        //put.setSecurityMethod(Standards.SECURITY_METHOD_CERT);
        transfer.getProtocols().add(put);
        
        xmlFilename = dataNode.getPath();
        ClientTransfer clientTransfer = vosClient.createTransfer(transfer);
        DoiInputStream doiStream = new DoiInputStream();
        clientTransfer.setInputStreamWrapper(doiStream);
        clientTransfer.run();

        if (clientTransfer.getThrowable() != null) {
            log.debug(clientTransfer.getThrowable().getMessage());
            // Get the message from the cause as it has far more context than
            // the throwable itself
            String message = clientTransfer.getThrowable().getMessage();
            if (message.contains("NodeNotFound")) {
                throw new ResourceNotFoundException(message, clientTransfer.getThrowable());
            }
            if (message.contains("PermissionDenied")) {
                throw new AccessControlException(message);
            }
            throw new RuntimeException(clientTransfer.getThrowable());
        }

        return doiStream.getResource();
    }

    private class DoiInputStream implements InputStreamWrapper {
        private Resource resource;

        public DoiInputStream() {
        }

        public void read(InputStream in) throws IOException {
            try {
                DoiXmlReader reader = new DoiXmlReader(true);
                resource = reader.read(in);
            } catch (DoiParsingException dpe) {
                throw new IOException("Error parsing " + xmlFilename + ": ", dpe);
            }
        }

        public Resource getResource() {
            return resource;
        }
    }
}
