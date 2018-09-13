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

import ca.nrc.cadc.net.InputStreamWrapper;
import ca.nrc.cadc.vos.Direction;
import ca.nrc.cadc.vos.Protocol;
import ca.nrc.cadc.vos.Transfer;
import ca.nrc.cadc.vos.VOS;
import ca.nrc.cadc.vos.client.ClientTransfer;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;

import org.apache.log4j.Logger;
import org.jdom2.Document;

/**
 *
 */
public class GetAction extends DOIAction {

    private static final Logger log = Logger.getLogger(GetAction.class);

    public GetAction() {
        super();
    }

    @Override
    public void doActionImpl() throws Exception {

        if (DOINumInputStr.equals("")) {
            // "Get All" not yet done
            throw new UnsupportedOperationException("\"Get All\" not implemented yet.");
        }
        else {
            // TODO: these request types are probably not needed
            // consider removing from here, DOIAction and related implementations
            requestType = GET_ONE_REQUEST;

            // Get DOI number from input
            String doiSuffix = DOINumInputStr;

            // Get path and filename for DOI Document stored in VOSpace
            String doiDatafileName = getDoiNodeUri(doiSuffix) + "/" + getDoiFilename(doiSuffix);

            getDoiDocFromVospace(doiDatafileName);

            // Write XML to output
            writeDoiDocToSyncOutput();
        }
    }

    private void writeDoiDocToSyncOutput () throws IOException {
        StringBuilder doiBuilder = new StringBuilder();
        String docFormat = this.syncInput.getHeader("Accept");
        log.debug("'Accept' value in header was " + docFormat);
        if (docFormat != null && docFormat.contains("application/json"))
        {
            // json document
            syncOutput.setHeader("Content-Type", "application/json");
            DoiJsonWriter writer = new DoiJsonWriter();
            writer.write(doiDocument, doiBuilder);
        }
        else
        {
            // xml document
            syncOutput.setHeader("Content-Type", "text/xml");
            DoiXmlWriter writer = new DoiXmlWriter();
            writer.write(doiDocument,doiBuilder);
        }
        syncOutput.getOutputStream().write(doiBuilder.toString().getBytes());
    }

    private void getDoiDocFromVospace (String dataNodePath) throws URISyntaxException {
        List<Protocol> protocols = new ArrayList<Protocol>();
        protocols.add(new Protocol(VOS.PROTOCOL_HTTPS_GET));
        Transfer transfer = new Transfer(new URI(dataNodePath), Direction.pullFromVoSpace, protocols);
        ClientTransfer clientTransfer = vosClient.createTransfer(transfer);
        clientTransfer.setInputStreamWrapper(new DoiInputStream());
        clientTransfer.run();
    }

    private class DoiInputStream implements InputStreamWrapper
    {
        private Document xmlDoc;

        public DoiInputStream() { }

        public void read(InputStream in) throws IOException
        {
            try {
                // TODO: turn this validation back on when doiResource (or whatever
                // the metadata class is going to be called) is changed over, so
                // this returns an instance of that class instead of a Document..
                DoiXmlReader reader = new DoiXmlReader(false);
                doiDocument = reader.read(in);
            } catch (DoiParsingException dpe) {
                throw new IOException(dpe);
            }
        }
    }
}
