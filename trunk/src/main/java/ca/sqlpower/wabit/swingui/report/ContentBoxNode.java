/*
 * Copyright (c) 2008, SQL Power Group Inc.
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

package ca.sqlpower.wabit.swingui.report;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.geom.Point2D;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

import javax.swing.AbstractAction;
import javax.swing.JDialog;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;

import org.apache.log4j.Logger;

import ca.sqlpower.swingui.DataEntryPanel;
import ca.sqlpower.swingui.DataEntryPanelBuilder;
import ca.sqlpower.swingui.SPSUtils;
import ca.sqlpower.wabit.WabitWorkspace;
import ca.sqlpower.wabit.report.CellSetRenderer;
import ca.sqlpower.wabit.report.ChartRenderer;
import ca.sqlpower.wabit.report.ContentBox;
import ca.sqlpower.wabit.report.ImageRenderer;
import ca.sqlpower.wabit.report.Label;
import ca.sqlpower.wabit.report.Page;
import ca.sqlpower.wabit.report.ReportContentRenderer;
import ca.sqlpower.wabit.report.ResultSetRenderer;
import edu.umd.cs.piccolo.PCamera;
import edu.umd.cs.piccolo.PCanvas;
import edu.umd.cs.piccolo.PNode;
import edu.umd.cs.piccolo.event.PBasicInputEventHandler;
import edu.umd.cs.piccolo.event.PInputEvent;
import edu.umd.cs.piccolo.event.PInputEventListener;
import edu.umd.cs.piccolo.util.PPaintContext;

public class ContentBoxNode extends PNode implements ReportNode {

    private static final Logger logger = Logger.getLogger(ContentBoxNode.class);
    
    private final ContentBox contentBox;

    private Color borderColour = new Color(0xcccccc);
    private BasicStroke borderStroke = new BasicStroke(1f);

    private Color textColour = Color.BLACK;

    private PInputEventListener inputHandler = new PBasicInputEventHandler() {
        
        @Override
        public void processEvent(PInputEvent event, int type) {
            super.processEvent(event, type);
            swingRenderer.processEvent(event, type);
        }
        
        @Override
        public void mouseClicked(PInputEvent event) {
            super.mouseClicked(event);
            if (event.getClickCount() == 2) {
                DataEntryPanel propertiesPanel = getPropertiesPanel();
                if (propertiesPanel == null) {
                    Toolkit.getDefaultToolkit().beep();
                } else {
                    String propertiesPanelName = "Properties for " + contentBox.getName();
                    JDialog d = DataEntryPanelBuilder.createDataEntryPanelDialog(
                            propertiesPanel, dialogOwner, propertiesPanelName, "OK");
                    d.setVisible(true);
                }
            }
            
        }
        
        @Override
        public void mousePressed(PInputEvent arg0) {
        	super.mousePressed(arg0);
        	maybeShowPopup(arg0);
        }
        
        @Override
        public void mouseReleased(PInputEvent arg0) {
        	super.mouseReleased(arg0);
        	maybeShowPopup(arg0);
        }
        
        /**
         * This will Display a List of options once you right click on the WorkspaceTree.
         */
        private void maybeShowPopup(PInputEvent e) {
        	if (!e.isPopupTrigger()) return;
        	JPopupMenu menu = new JPopupMenu();
        	JMenuItem properties = new JMenuItem(new AbstractAction() {
				public void actionPerformed(ActionEvent arg0) {
		        	DataEntryPanel propertiesPanel = getPropertiesPanel();
		            if (propertiesPanel == null) {
		                Toolkit.getDefaultToolkit().beep();
		            } else {
		                String propertiesPanelName = "Properties for " + contentBox.getName();
		                JDialog d = DataEntryPanelBuilder.createDataEntryPanelDialog(
		                        propertiesPanel, dialogOwner, propertiesPanelName, "OK");
		                d.setVisible(true);
		            }
				}
        	});
        	properties.setText("Properties");
        	menu.add(properties);
        	menu.addSeparator();
        	
        	final PNode node = getParent();
        	JMenuItem delete = new JMenuItem(new AbstractAction() {
				public void actionPerformed(ActionEvent e) {
					node.removeChild(ContentBoxNode.this);
				}
        	});
        	delete.setText("Delete");
        	menu.add(delete);
        	Point2D position = e.getPosition();
        	final PCanvas canvas = (PCanvas) e.getComponent();
			menu.show(canvas, (int) position.getX(), (int) position.getY());
        }
        
    };
    
    /**
     * Reacts to changes in the content box by repainting this pnode.
     */
    private final PropertyChangeListener modelChangeHandler = new PropertyChangeListener() {
        public void propertyChange(PropertyChangeEvent evt) {
        	updateBoundsFromContentBox();
            repaint();
            
        }
    };
    
    private final Window dialogOwner;

    /**
     * This {@link ResultSetRenderer} compliment will handle operations on the
     * renderer that are swing specific.
     */
    private final SwingContentRenderer swingRenderer;
    
    public ContentBoxNode(Window dialogOwner, WabitWorkspace workspace, ReportLayoutPanel parentPanel, ContentBox contentBox) {
        this.dialogOwner = dialogOwner;
        logger.debug("Creating new contentboxnode for " + contentBox);
        this.contentBox = contentBox;
        
        final ReportContentRenderer renderer = contentBox.getContentRenderer();
        if (renderer instanceof CellSetRenderer) {
            swingRenderer = new CellSetSwingRenderer((CellSetRenderer) renderer);
        } else if (renderer instanceof ResultSetRenderer) {
            swingRenderer = new ResultSetSwingRenderer((ResultSetRenderer) renderer, parentPanel);
        } else if (renderer instanceof ImageRenderer) {
            swingRenderer = new ImageSwingRenderer(workspace, (ImageRenderer) renderer);
        } else if (renderer instanceof ChartRenderer) {
            swingRenderer = new ChartSwingRenderer(workspace, (ChartRenderer) renderer);
        } else if (renderer instanceof Label) {
            swingRenderer = new SwingLabel((Label) renderer);
        } else if (renderer == null) {
            swingRenderer = null;
        } else {
            throw new IllegalStateException("Unknown renderer of type " + renderer.getClass() 
                    + ". The swing components of this renderer type are missing.");
        }
        
        setBounds(contentBox.getX(), contentBox.getY(), contentBox.getWidth(), contentBox.getHeight());
        addInputEventListener(inputHandler);
        contentBox.addPropertyChangeListener(modelChangeHandler);
        updateBoundsFromContentBox();

    }
    
    private void updateBoundsFromContentBox() {
        super.setBounds(contentBox.getX(), contentBox.getY(),
        		contentBox.getWidth(), contentBox.getHeight());
    }
    
    @Override
    public boolean setBounds(double x, double y, double width, double height) {
    	logger.debug("settingBounds: x="+x+" y="+y+" width="+width+" height="+ height);
    	contentBox.setX(x);
    	contentBox.setY(y);
    	contentBox.setWidth(width);
    	contentBox.setHeight(height);
        return true;
    }
    
    @Override
    protected void paint(PPaintContext paintContext) {
    	ReportContentRenderer contentRenderer = contentBox.getContentRenderer();
    	if (contentRenderer != null && contentRenderer.getBackgroundColour() != null) {
    		setPaint(contentRenderer.getBackgroundColour());
    	}
        super.paint(paintContext);
        PCamera camera = paintContext.getCamera();
        Graphics2D g2 = paintContext.getGraphics();
        
        g2.setColor(borderColour);
        g2.setStroke(SPSUtils.getAdjustedStroke(borderStroke, camera.getViewScale()));
        g2.draw(getBounds());
        
        g2.setColor(textColour);
        
        if (contentRenderer != null) {
            logger.debug("Rendering content");
            Graphics2D contentGraphics = (Graphics2D) g2.create(
                    (int) getX(), (int) getY(),
                    (int) getWidth(), (int) getHeight());
            contentGraphics.setFont(contentBox.getFont()); // XXX could use piccolo attribute to do this magically
            contentRenderer.resetToFirstPage();
            contentRenderer.renderReportContent(contentGraphics, contentBox, camera.getViewScale(), 0, false);
            contentGraphics.dispose();
        } else {
            g2.setColor(Color.LIGHT_GRAY);
            g2.drawString("Empty box\u2014drag content provider here!", 0, (int) (getHeight() / 2));
        }
    }
    @Override
    public void offset(double dx, double dy) {
    	logger.debug("setting offset: x="+dx+" y="+dy);
    	double x = contentBox.getX() + dx;
    	double y = contentBox.getY() + dy;
    	contentBox.setX(x);
    	contentBox.setY(y);
    }
    @Override
    public void setParent(PNode newParent) {
        
        if (newParent instanceof PageNode) {
            Page p = ((PageNode) newParent).getModel();
            if (contentBox.getParent() != null) {
                if (p != contentBox.getParent()) {
                    contentBox.getParent().removeContentBox(contentBox);
                    p.addContentBox(contentBox);
                }
            }
        } else if ( newParent == null) {
        	contentBox.getParent().removeContentBox(contentBox);
        }
        super.setParent(newParent);
    }

    public void cleanup() {
        contentBox.removePropertyChangeListener(modelChangeHandler);
    }

    public ContentBox getModel() {
        return contentBox;
    }

    public DataEntryPanel getPropertiesPanel() {
        if (contentBox.getContentRenderer() != null) {
            DataEntryPanel propertiesPanel = swingRenderer.getPropertiesPanel();
            if (propertiesPanel == null) {
                logger.debug("Content renderer has no properties dialog: " + contentBox.getContentRenderer());
            }
            return propertiesPanel;
        } else {
            logger.debug("Content box has no renderer: " + contentBox);
            return null;
        }
    }
    
    public PInputEventListener getInputHandler() {
    	return inputHandler;
    }
}