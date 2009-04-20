/*
 * Copyright (c) 2009, SQL Power Group Inc.
 *
 * This file is part of Wabit.
 *
 * Wabit is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 3 of the License, or
 * (at your option) any later version.
 *
 * Wabit is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>. 
 */

package ca.sqlpower.wabit.swingui.action;

import java.awt.Component;
import java.awt.event.ActionEvent;
import java.io.IOException;
import java.net.URI;

import javax.swing.AbstractAction;
import javax.swing.JOptionPane;

import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.log4j.Logger;

import ca.sqlpower.swingui.SPSUtils;
import ca.sqlpower.wabit.WabitSession;
import ca.sqlpower.wabit.WabitUtils;
import ca.sqlpower.wabit.dao.LoadProjectXMLDAO;
import ca.sqlpower.wabit.enterprise.client.WabitServerInfo;
import ca.sqlpower.wabit.enterprise.client.WabitServerSessionContext;
import ca.sqlpower.wabit.swingui.WabitSwingSession;
import ca.sqlpower.wabit.swingui.WabitSwingSessionContext;
import ca.sqlpower.wabit.swingui.WabitSwingSessionContextImpl;

public class OpenProjectOnServerAction extends AbstractAction {

    private static final Logger logger = Logger.getLogger(OpenProjectOnServerAction.class);
    private final WabitServerInfo serviceInfo;
    private final String projectName;
    private final Component dialogOwner;

    public OpenProjectOnServerAction(
            Component dialogOwner,
            WabitServerInfo si,
            String projectName) {
        super(projectName);
        this.dialogOwner = dialogOwner;
        this.serviceInfo = si;
        if (si == null) {
            throw new NullPointerException("Null service info");
        }
        this.projectName = projectName;
    }

    public void actionPerformed(ActionEvent e) {
        HttpClient httpclient = new DefaultHttpClient();
        try {
            final WabitSwingSessionContext sessionContext =
                new WabitSwingSessionContextImpl(WabitServerSessionContext.getInstance(serviceInfo), false);
            String contextPath = serviceInfo.getPath();
            URI uri = new URI("http", null, serviceInfo.getServerAddress(), serviceInfo.getPort(),
                    contextPath + "project/" + projectName, null, null);
            HttpGet httpget = new HttpGet(uri);
            logger.debug("executing request " + httpget.getURI());

            ResponseHandler<Void> responseHandler = new ResponseHandler<Void>() {

                public Void handleResponse(HttpResponse response)
                        throws ClientProtocolException, IOException {

                    if (response.getStatusLine().getStatusCode() != 200) {
                        JOptionPane.showMessageDialog(dialogOwner,
                                "Server reported error:\n" + response.getStatusLine() +
                                "\nWhile requesting project \""+projectName+"\"",
                                "Open On Server Failed", JOptionPane.ERROR_MESSAGE);
                        return null;
                    }
                    try {
                        LoadProjectXMLDAO projectLoader = new LoadProjectXMLDAO(
                                sessionContext, response.getEntity().getContent());
                        for (WabitSession session : projectLoader.loadProjects()) {
                            WabitSwingSession swingSession = (WabitSwingSession) session;
                            swingSession.buildUI();
                            // kinda have to set current "file" (but it's not exactly a file...)
                        }
                    } catch (Exception ex) {
                        throw new RuntimeException(ex);
                    }
                    return null;
                }
                
            };
            httpclient.execute(httpget, responseHandler);
            
        } catch (Exception ex) {
            SPSUtils.showExceptionDialogNoReport(dialogOwner,
                    "Failed to retrieve project list from server " + WabitUtils.serviceInfoSummary(serviceInfo), ex);
        } finally {
            httpclient.getConnectionManager().shutdown();
        }
    }
    
}
