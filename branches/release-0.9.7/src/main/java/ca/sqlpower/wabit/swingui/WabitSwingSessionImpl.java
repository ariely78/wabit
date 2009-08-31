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

package ca.sqlpower.wabit.swingui;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

import javax.naming.NamingException;
import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JSplitPane;
import javax.swing.JTree;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.WindowConstants;
import javax.swing.border.EmptyBorder;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.TreeModelEvent;
import javax.swing.tree.TreePath;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.lf5.viewer.categoryexplorer.TreeModelAdapter;
import org.olap4j.OlapConnection;

import ca.sqlpower.sql.JDBCDataSource;
import ca.sqlpower.sql.Olap4jDataSource;
import ca.sqlpower.sql.SPDataSource;
import ca.sqlpower.sqlobject.SQLDatabase;
import ca.sqlpower.sqlobject.SQLObjectException;
import ca.sqlpower.swingui.MemoryMonitor;
import ca.sqlpower.swingui.SPSUtils;
import ca.sqlpower.swingui.SPSwingWorker;
import ca.sqlpower.swingui.SwingUIUserPrompterFactory;
import ca.sqlpower.swingui.db.DatabaseConnectionManager;
import ca.sqlpower.swingui.db.DefaultDataSourceDialogFactory;
import ca.sqlpower.swingui.db.DefaultDataSourceTypeDialogFactory;
import ca.sqlpower.swingui.event.SessionLifecycleEvent;
import ca.sqlpower.swingui.event.SessionLifecycleListener;
import ca.sqlpower.util.UserPrompter;
import ca.sqlpower.util.UserPrompterFactory;
import ca.sqlpower.util.UserPrompter.UserPromptOptions;
import ca.sqlpower.util.UserPrompter.UserPromptResponse;
import ca.sqlpower.util.UserPrompterFactory.UserPromptType;
import ca.sqlpower.wabit.QueryCache;
import ca.sqlpower.wabit.WabitObject;
import ca.sqlpower.wabit.WabitSession;
import ca.sqlpower.wabit.WabitSessionContextImpl;
import ca.sqlpower.wabit.WabitVersion;
import ca.sqlpower.wabit.WabitWorkspace;
import ca.sqlpower.wabit.dao.WorkspaceXMLDAO;
import ca.sqlpower.wabit.enterprise.client.WabitServerInfo;
import ca.sqlpower.wabit.olap.OlapConnectionPool;
import ca.sqlpower.wabit.olap.OlapQuery;
import ca.sqlpower.wabit.report.Layout;
import ca.sqlpower.wabit.swingui.action.AboutAction;
import ca.sqlpower.wabit.swingui.action.HelpAction;
import ca.sqlpower.wabit.swingui.action.ImportWorkspaceAction;
import ca.sqlpower.wabit.swingui.action.NewServerWorkspaceAction;
import ca.sqlpower.wabit.swingui.action.NewWorkspaceAction;
import ca.sqlpower.wabit.swingui.action.OpenWorkspaceAction;
import ca.sqlpower.wabit.swingui.action.SaveServerWorkspaceAction;
import ca.sqlpower.wabit.swingui.action.SaveWorkspaceAction;
import ca.sqlpower.wabit.swingui.action.SaveWorkspaceAsAction;
import ca.sqlpower.wabit.swingui.olap.OlapQueryPanel;
import ca.sqlpower.wabit.swingui.report.ReportLayoutPanel;
import ca.sqlpower.wabit.swingui.tree.WorkspaceTreeCellEditor;
import ca.sqlpower.wabit.swingui.tree.WorkspaceTreeCellRenderer;
import ca.sqlpower.wabit.swingui.tree.WorkspaceTreeModel;

import com.jgoodies.forms.builder.DefaultFormBuilder;
import com.jgoodies.forms.layout.FormLayout;


/**
 * The Main Window for the Wabit Application; contains a main() method that is
 * the conventional way to start the application running.
 */
public class WabitSwingSessionImpl implements WabitSwingSession {
    
    private static final Icon DB_ICON = new ImageIcon(WabitSwingSessionImpl.class.getClassLoader().getResource("icons/dataSources-db.png"));
	
    /**
	 * The icon for the "Open Demonstration Workspace" button.
	 */
	private static final Icon OPEN_DEMO_ICON = new ImageIcon(WabitWelcomeScreen.class.getClassLoader().getResource("icons/wabit-16.png"));
    
	/**
	 * A constant for storing the location of the query dividers in prefs.
	 */
	private static final String QUERY_DIVIDER_LOCATON = "QueryDividerLocaton";

	/**
	 * A constant for storing the location of the divider for layouts in prefs.
	 */
	private static final String LAYOUT_DIVIDER_LOCATION = "LayoutDividerLocation";
	
	private static Logger logger = Logger.getLogger(WabitSwingSessionImpl.class);
	
	private class WindowClosingListener extends WindowAdapter {
		
		private final WabitSwingSession session;

		public WindowClosingListener(WabitSwingSession session) {
			this.session = session;
		}
		
		@Override
		public void windowClosing(WindowEvent e) {
	    	session.close();
		}
	}

	private final WabitSwingSessionContext sessionContext;
	
	private final WabitWorkspace workspace;
	
	private JTree workspaceTree;
	private JSplitPane wabitPane;
	private final JFrame frame;
	private static JLabel statusLabel;
	
	public static final ImageIcon FRAME_ICON = new ImageIcon(WabitSwingSessionImpl.class.getResource("/icons/wabit-16.png"));

	private static final int DEFAULT_DIVIDER_LOC = 50;

	/**
	 * @see #isLoading()
	 */
	private boolean loading;

	private final Preferences prefs = Preferences.userNodeForPackage(WabitSwingSessionImpl.class);
	
    /**
     * The database instances we've created due to calls to {@link #getDatabase(SPDataSource)}.
     */
    private final Map<SPDataSource, SQLDatabase> databases = new HashMap<SPDataSource, SQLDatabase>();
    
	/**
	 * The list of all currently-registered background tasks.
	 */
	private final List<SPSwingWorker> activeWorkers =
		Collections.synchronizedList(new ArrayList<SPSwingWorker>());
	
	private final List<SessionLifecycleListener<WabitSession>> lifecycleListeners =
		new ArrayList<SessionLifecycleListener<WabitSession>>();

	/**
	 * This is the current panel to the right of the JTree showing the parts of the 
	 * workspace. This will allow editing the currently selected element in the JTree.
	 */
	private WabitPanel currentEditorPanel;

	/**
	 * This DB connection manager will allow editing the db connections in the
	 * pl.ini file. This DB connection manager can be used anywhere needed in 
	 * wabit. 
	 */
	private DatabaseConnectionManager dbConnectionManager;
	
	/**
	 * This is the most recent file loaded in this session or the last file that the session
	 * was saved to. This will be null if no file has been loaded or the workspace has not
	 * been saved yet.
	 */
	private File currentFile = null;
	
	/**
	 * A {@link UserPrompterFactory} that will create a dialog for users to choose an existing
	 * DB or create a new one if they load a workspace with a DB not in their pl.ini.
	 */
	private UserPrompterFactory upfMissingLoadedDB;
	
	/**
	 * This action will close all of the open sessions and, if successful, close the app.
	 */
	private final Action exitAction = new AbstractAction("Exit") {
		public void actionPerformed(ActionEvent e) {
			getContext().close();
		}
	};

	private AboutAction aboutAction;
	
	private final PropertyChangeListener editorModelListener = new PropertyChangeListener() {
		public void propertyChange(PropertyChangeEvent evt) {
		    if (isLoading()) return;
			if (evt.getPropertyName().equals("editorPanelModel")) {
				if (!setEditorPanel((WabitObject) evt.getNewValue())) {
					workspace.setEditorPanelModel((WabitObject) evt.getOldValue());
					return;
				}
				if (evt.getNewValue() != null) {
					final TreePath createTreePathForObject = workspaceTreeModel.createTreePathForObject((WabitObject) evt.getNewValue());
					logger.debug("Tree path being set to " + createTreePathForObject + " as editor panel being set to " + ((WabitObject) evt.getNewValue()).getName());
					workspaceTree.setSelectionPath(createTreePathForObject);
				}
			}
		}
	};

	/**
	 * The model behind the workspace tree on the left side of Wabit.
	 */
	private WorkspaceTreeModel workspaceTreeModel;

	/**
	 * This is the limit of all result sets in Wabit. Changing this spinner
	 * will cause cached result sets to be flushed.
	 */
	private final JSpinner rowLimitSpinner;
	
	private final PropertyChangeSupport pcs = new PropertyChangeSupport(this);
	
	/**
	 * This tracks the old row limit for firing an appropriate event when the row
	 * limit spinner changes.
	 */
	private int oldRowLimitValue;
	
	/**
     * The connection pools we've created due to calling {@link #createConnection(Olap4jDataSource)}.
     */
    private final Map<Olap4jDataSource, OlapConnectionPool> olapConnectionPools = new HashMap<Olap4jDataSource, OlapConnectionPool>();
	
	/**
	 * Creates a new session 
	 * 
	 * @param context
	 */
	public WabitSwingSessionImpl(WabitSwingSessionContext context) {
	    workspace = new WabitWorkspace();
	    workspace.addPropertyChangeListener(editorModelListener);
		sessionContext = context;
		sessionContext.registerChildSession(this);
		
		statusLabel= new JLabel();
		
		wabitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
		workspaceTreeModel = new WorkspaceTreeModel(workspace);
		workspaceTree = new JTree(workspaceTreeModel);
		workspaceTree.setToggleClickCount(0);
		
        rowLimitSpinner = new JSpinner();
        final JSpinner.NumberEditor rowLimitEditor = new JSpinner.NumberEditor(getRowLimitSpinner());
		getRowLimitSpinner().setEditor(rowLimitEditor);
        getRowLimitSpinner().setValue(1000);
        rowLimitSpinner.addChangeListener(new ChangeListener() {
			public void stateChanged(ChangeEvent e) {
				pcs.firePropertyChange(QueryCache.ROW_LIMIT, oldRowLimitValue, ((Integer) rowLimitSpinner.getValue()).intValue());
				oldRowLimitValue = (Integer) rowLimitSpinner.getValue();
			}
		});
        
        //Temporary upfMissingLoadedDB factory that is not parented in case there is no frame at current.
        //This should be replaced in the buildUI with a properly parented prompter factory.
        upfMissingLoadedDB = new SwingUIUserPrompterFactory(null, sessionContext.getDataSources());
		
        frame = new JFrame("Wabit " + WabitVersion.VERSION + " - " + sessionContext.getName());
	}
	
	/**
	 * sets the StatusMessage
	 */
	public static void setStatusMessage (String msg) {
		statusLabel.setText(msg);	
	}
	
	/**
	 *  Builds the GUI
	 * @throws SQLObjectException 
	 */
    public void buildUI() throws SQLObjectException {
		frame.setIconImage(FRAME_ICON.getImage());
		frame.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
		frame.addWindowListener(new WindowClosingListener(this));
		aboutAction = new AboutAction(frame);
		
		List<Class<? extends SPDataSource>> newDSTypes = new ArrayList<Class<? extends SPDataSource>>();
        newDSTypes.add(JDBCDataSource.class);
        newDSTypes.add(Olap4jDataSource.class);
        dbConnectionManager = new DatabaseConnectionManager(getContext().getDataSources(), 
				new DefaultDataSourceDialogFactory(), 
				new DefaultDataSourceTypeDialogFactory(getContext().getDataSources()),
				new ArrayList<Action>(), new ArrayList<JComponent>(), frame, false, newDSTypes);
		dbConnectionManager.setDbIcon(DB_ICON);
		
		upfMissingLoadedDB = new SwingUIUserPrompterFactory(frame, sessionContext.getDataSources());
        
        // this will be the frame's content pane
		JPanel cp = new JPanel(new BorderLayout());
    	
		final WorkspaceTreeCellRenderer renderer = new WorkspaceTreeCellRenderer();
		workspaceTree.setCellRenderer(renderer);
		workspaceTree.setCellEditor(new WorkspaceTreeCellEditor(workspaceTree, renderer));
		
		for (final QueryCache queryCache : workspace.getQueries()) {
		    //Repaints the tree when the worker thread's timer fires. This will allow the tree node
		    //to paint a throbber badge on the query node.
		    queryCache.addTimerListener(new PropertyChangeListener() {
		        public void propertyChange(PropertyChangeEvent evt) {
		            renderer.updateTimer(queryCache, (Integer) evt.getNewValue());
		            workspaceTree.repaint(workspaceTree.getPathBounds(new TreePath(new WabitObject[]{workspace, queryCache})));
		        }
		    });

		    //This removes the timer from the query if the query has stopped running.
		    queryCache.addPropertyChangeListener(new PropertyChangeListener() {
		        public void propertyChange(PropertyChangeEvent evt) {
		            if (evt.getPropertyName().equals(QueryCache.RUNNING) && !((Boolean) evt.getNewValue())) {
		                renderer.removeTimer(queryCache);
		                workspaceTree.repaint(workspaceTree.getPathBounds(new TreePath(new WabitObject[]{workspace, queryCache})));
		            }
		        }
		    });
		}
		
		workspaceTree.getModel().addTreeModelListener(new TreeModelAdapter() {
			@Override
			public void treeNodesInserted(final TreeModelEvent e) {
				for (int i = 0; i < e.getChildren().length; i++) {
					if (e.getChildren()[i] instanceof QueryCache) {
						final QueryCache queryCache = (QueryCache) e.getChildren()[i];
						final TreePath treePath = e.getTreePath();
						queryCache.addTimerListener(new PropertyChangeListener() {
							public void propertyChange(PropertyChangeEvent evt) {
								renderer.updateTimer(queryCache, (Integer) evt.getNewValue());
								workspaceTree.repaint(workspaceTree.getPathBounds(new TreePath(new WabitObject[]{workspace, queryCache})));
							}
						});
						queryCache.addPropertyChangeListener(new PropertyChangeListener() {
							public void propertyChange(PropertyChangeEvent evt) {
								if (evt.getPropertyName().equals("running") && !((Boolean) evt.getNewValue())) {
									renderer.removeTimer(queryCache);
									workspaceTree.repaint(workspaceTree.getPathBounds(new TreePath(new WabitObject[]{workspace, queryCache})));
								}
							}
						});
					}
				}
			}
		});
		workspaceTree.addMouseListener(new WorkspaceTreeListener(this));
    	workspaceTree.setEditable(true);

        wabitPane.add(new JScrollPane(SPSUtils.getBrandedTreePanel(workspaceTree)), JSplitPane.LEFT);
        if (workspace.getEditorPanelModel() == null) {
        	workspace.setEditorPanelModel(workspace);
        } else if (currentEditorPanel != null) {
            // This code was here, but I'm not sure if this actually does anything,
            // since the frame hasn't been realized yet...
        	currentEditorPanel.getPanel().repaint();
        }
    	
		//prefs
    	if(prefs.get("MainDividerLocaton", null) != null) {
            String[] dividerLocations = prefs.get("MainDividerLocaton", null).split(",");
            wabitPane.setDividerLocation(Integer.parseInt(dividerLocations[0]));
        }
        
    	DefaultFormBuilder statusBarBuilder = new DefaultFormBuilder(new FormLayout("pref:grow, 4dlu, pref, 2dlu, max(50dlu; pref), 4dlu, pref"));
        statusBarBuilder.append(statusLabel);
		
        statusBarBuilder.append("Row Limit", getRowLimitSpinner());
        
		MemoryMonitor memoryMonitor = new MemoryMonitor();
		memoryMonitor.start();
		JLabel memoryLabel = memoryMonitor.getLabel();
		memoryLabel.setBorder(new EmptyBorder(0, 20, 0, 20));
		statusBarBuilder.append(memoryLabel);
		
		cp.add(wabitPane, BorderLayout.CENTER);
        cp.add(statusBarBuilder.getPanel(), BorderLayout.SOUTH);
        
        JMenuBar menuBar = new JMenuBar();
		JMenu fileMenu = new JMenu("File");
		fileMenu.setMnemonic('f');
		menuBar.add(fileMenu);
		fileMenu.add(new NewWorkspaceAction(getContext()));
		fileMenu.add(new OpenWorkspaceAction(this, this.getContext()));
		fileMenu.add(getContext().createRecentMenu());
		fileMenu.add(new ImportWorkspaceAction(this));
		
		fileMenu.addSeparator();
		JMenuItem openDemoMenuItem = new JMenuItem(new AbstractAction() {
			public void actionPerformed(ActionEvent e) {
				OpenWorkspaceAction.loadFile(WabitWelcomeScreen.class.getResourceAsStream(
				        "/ca/sqlpower/wabit/example_workspace.wabit"), getContext());
			}
		});
		
        fileMenu.add(getContext().createServerListMenu(frame, "New Server Workspace", new ServerListMenuItemFactory() {
            public JMenuItem createMenuEntry(WabitServerInfo serviceInfo, Component dialogOwner) {
                return new JMenuItem(new NewServerWorkspaceAction(dialogOwner, serviceInfo));
            }
        }));
        fileMenu.add(getContext().createServerListMenu(frame, "Open Server Workspace", new ServerListMenuItemFactory() {
            public JMenuItem createMenuEntry(WabitServerInfo serviceInfo, Component dialogOwner) {
                return new OpenOnServerMenu(dialogOwner, serviceInfo);
            }
        }));
        
        fileMenu.addSeparator();
		openDemoMenuItem.setText("Open Demo Workspace");
		openDemoMenuItem.setIcon(OPEN_DEMO_ICON);
		fileMenu.add(openDemoMenuItem);
		
		fileMenu.addSeparator();
		fileMenu.add(new SaveWorkspaceAction(this));
		fileMenu.add(new SaveWorkspaceAsAction(this));
		fileMenu.add(getContext().createServerListMenu(frame, "Save Workspace on Server", new ServerListMenuItemFactory() {
            public JMenuItem createMenuEntry(WabitServerInfo serviceInfo, Component dialogOwner) {
                try {
                    return new JMenuItem(new SaveServerWorkspaceAction(serviceInfo, dialogOwner, workspace));
                } catch (Exception e) {
                    JMenuItem menuItem = new JMenuItem(e.toString());
                    menuItem.setEnabled(false);
                    // TODO it would be nice to have ASUtils.createExceptionMenuItem(Throwable)
                    return menuItem;
                }
            }
		}));
		fileMenu.addSeparator();
		fileMenu.add(new AbstractAction("Close Workspace") {
			public void actionPerformed(ActionEvent e) {
				close();
			}
		});
		fileMenu.addSeparator();
		JMenuItem databaseConnectionManager = new JMenuItem(new AbstractAction("Database Connection Manager...") {
			public void actionPerformed(ActionEvent e) {
				 dbConnectionManager.showDialog(getFrame());
			}
		});
		fileMenu.add(databaseConnectionManager);

		
		if (!getContext().isMacOSX()) {
			fileMenu.addSeparator();
			fileMenu.add(exitAction);
		}
		
		JMenu viewMenu = new JMenu("View");
		viewMenu.setMnemonic('v');
		menuBar.add(viewMenu);
		JMenuItem maxEditor = new JMenuItem(new AbstractAction("Maximize Editor") {
			public void actionPerformed(ActionEvent e) {
				if (currentEditorPanel != null) {
					currentEditorPanel.maximizeEditor();
				}
			}
		});
		maxEditor.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, InputEvent.CTRL_DOWN_MASK));
		viewMenu.add(maxEditor);
        
		JMenu helpMenu = new JMenu("Help");
		helpMenu.setMnemonic('h');
		menuBar.add(helpMenu);
		if (!getContext().isMacOSX()) {
			helpMenu.add(aboutAction);
            helpMenu.addSeparator();
        }
		helpMenu.add(SPSUtils.forumAction);
		helpMenu.add(new HelpAction(frame));
	
		frame.setJMenuBar(menuBar);
        frame.setContentPane(cp);
        
        //prefs
        if (prefs.get("frameBounds", null) != null) {
            String[] frameBounds = prefs.get("frameBounds", null).split(",");
            if (frameBounds.length == 4) {
            	logger.debug("Frame bounds are " + Integer.parseInt(frameBounds[0]) + ", " + Integer.parseInt(frameBounds[1]) + ", " +
                        Integer.parseInt(frameBounds[2]) + ", " + Integer.parseInt(frameBounds[3]));
                frame.setBounds(
                        Integer.parseInt(frameBounds[0]),
                        Integer.parseInt(frameBounds[1]),
                        Integer.parseInt(frameBounds[2]),
                        Integer.parseInt(frameBounds[3]));
            }
        } else {
        	frame.setSize(1050, 750);
        	frame.setLocation(200, 100);
        }

        frame.setVisible(true);
        
        logger.debug("UI is built.");
    }
    
    public JTree getTree() {
    	return workspaceTree;
    }

    /* docs inherited from interface */
	public void registerSwingWorker(SPSwingWorker worker) {
		activeWorkers.add(worker);
	}

    /* docs inherited from interface */
	public void removeSwingWorker(SPSwingWorker worker) {
		activeWorkers.remove(worker);
	}

	public void addSessionLifecycleListener(SessionLifecycleListener<WabitSession> l) {
		lifecycleListeners.add(l);
	}

	public void removeSessionLifecycleListener(SessionLifecycleListener<WabitSession> l) {
		lifecycleListeners.remove(l);
	}
	
	public boolean close() {
		if (!removeEditorPanel()) {
    		return false;
    	}
		
    	try {
        	prefs.put("MainDividerLocaton", String.format("%d", wabitPane.getDividerLocation()));
            prefs.put("frameBounds", String.format("%d,%d,%d,%d", frame.getX(), frame.getY(), frame.getWidth(), frame.getHeight()));
            prefs.flush();
        } catch (BackingStoreException ex) {
            logger.log(Level.WARN,"Failed to flush preferences", ex);
        }
        
        boolean closing = false;
		if (hasUnsavedChanges()) {
			int response = JOptionPane.showOptionDialog(frame,
					"You have unsaved changes. Do you want to save?", "Unsaved Changes", //$NON-NLS-1$ //$NON-NLS-2$
                    JOptionPane.YES_NO_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE, null,
                    new Object[] {"Don't Save", "Cancel", "Save"}, "Save"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
            if (response == 0) {
                closing = true;
            } else if (response == JOptionPane.CLOSED_OPTION || response == 1) {
            	setEditorPanel(workspace.getEditorPanelModel());
            	return false;
            } else {
            	if (SaveWorkspaceAction.save(WabitSwingSessionImpl.this)) {
            		closing = true;
            	}
            }
		} else {
			closing = true;
		}
		
		if (closing) {
	    	SessionLifecycleEvent<WabitSession> lifecycleEvent =
	    		new SessionLifecycleEvent<WabitSession>(this);
	    	for (int i = lifecycleListeners.size() - 1; i >= 0; i--) {
	    		lifecycleListeners.get(i).sessionClosing(lifecycleEvent);
	    	}
	    	
	    	sessionContext.deregisterChildSession(this);
	    	
	    	for (SQLDatabase db : databases.values()) {
	    	    db.disconnect();
	    	}
	    	
	    	for (OlapConnectionPool olapPool : olapConnectionPools.values()) {
	    	    try {
                    olapPool.disconnect();
                } catch (SQLException e) {
                    throw new RuntimeException(e);
                }
	    	}
	    	
    		frame.dispose();
		}
		return closing;
	}

    /**
     * Launches the Wabit application by loading the configuration and
     * displaying the GUI.
     * 
     * @throws Exception if startup fails
     */
    public static void  main(final String[] args) throws Exception {
    	System.setProperty("com.apple.mrj.application.apple.menu.about.name", "Wabit");
    	System.setProperty("apple.laf.useScreenMenuBar", "true");
    	try {
    		UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
    	} catch (Exception e) {
    		logger.error("Unable to set native look and feel. Continuing with default.", e);
    	}

    	SwingUtilities.invokeLater(new Runnable() {

			public void run() {
				try {
				    WabitSessionContextImpl coreContext = new WabitSessionContextImpl(false, true);
					WabitSwingSessionContext context = new WabitSwingSessionContextImpl(coreContext, false);
					
					final File importFile;
					if (args.length > 0) {
						importFile = new File(args[0]);
					} else if (context.startOnWelcomeScreen()) {
						importFile = null;
					} else {
						importFile = context.createRecentMenu().getMostRecentFile();
					}
					
					WabitSwingSessionImpl wss;
					
					if (importFile != null) {
						try {
							OpenWorkspaceAction.loadFile(importFile, context);
						} catch (Throwable e) {
							context.getWelcomeScreen().showFrame();
						}
					} else {
						new WabitWelcomeScreen(context).showFrame();
					}
				} catch (Exception ex) {
					 ex.printStackTrace();
					// We wish we had a parent component to direct the dialog but this is being invoked, so
					// everything else blew up.
					SPSUtils.showExceptionDialogNoReport("An unexpected error occured while launching Wabit",ex);
				}
			}
    	});
    	
    }

    public WabitWorkspace getWorkspace() {
        return workspace;
    }
    
	public WabitSwingSessionContext getContext() {
		return sessionContext;
	}
	
	public JFrame getFrame() {
		return frame;
	}
	
	public boolean setEditorPanel(WabitObject entryPanelModel) {
	    if (isLoading()) return false;
		if (!removeEditorPanel()) {
			return false;
		}
		int dividerLoc;
		if (currentEditorPanel != null) {
			dividerLoc = wabitPane.getDividerLocation();
		} else {
	    	if(prefs.get("MainDividerLocaton", null) != null) {
	            String[] dividerLocations = prefs.get("MainDividerLocaton", null).split(",");
	            dividerLoc = Integer.parseInt(dividerLocations[0]);
	        } else {
	        	dividerLoc = DEFAULT_DIVIDER_LOC;
	        }
		}
		
		if (currentEditorPanel != null) {
			wabitPane.remove(currentEditorPanel.getPanel());
		}
		
		if (entryPanelModel instanceof QueryCache) {
			QueryPanel queryPanel = new QueryPanel(this, (QueryCache) entryPanelModel);
		   	if (prefs.get(QUERY_DIVIDER_LOCATON, null) != null) {
	            String[] dividerLocations = prefs.get(QUERY_DIVIDER_LOCATON, null).split(",");
	            queryPanel.getTopRightSplitPane().setDividerLocation(Integer.parseInt(dividerLocations[0]));
	            queryPanel.getFullSplitPane().setDividerLocation(Integer.parseInt(dividerLocations[1]));
		   	}
		   	currentEditorPanel = queryPanel;
		} else if (entryPanelModel instanceof OlapQuery) {
		    OlapQueryPanel panel = new OlapQueryPanel(this, wabitPane, (OlapQuery) entryPanelModel);
		    currentEditorPanel = panel;
		} else if (entryPanelModel instanceof Layout) {
			ReportLayoutPanel rlPanel = new ReportLayoutPanel(this, (Layout) entryPanelModel);
			if (prefs.get(LAYOUT_DIVIDER_LOCATION, null) != null) {
				rlPanel.getSplitPane().setDividerLocation(Integer.parseInt(prefs.get(LAYOUT_DIVIDER_LOCATION, null)));
			}
			currentEditorPanel = rlPanel;
		} else if (entryPanelModel instanceof WabitWorkspace) {
			currentEditorPanel = new WorkspacePanel(this);
		} else {
			if (entryPanelModel instanceof WabitObject && ((WabitObject) entryPanelModel).getParent() != null) {
				setEditorPanel(((WabitObject) entryPanelModel).getParent()); 
			} else {
				throw new IllegalStateException("Unknown model for the defined types of entry panels. The type is " + entryPanelModel.getClass());
			}
		}
		wabitPane.add(currentEditorPanel.getPanel(), JSplitPane.RIGHT);
		wabitPane.setDividerLocation(dividerLoc);
		// The execute query currently needs to be done after the panel is added
		// to the split pane, because it requires a Graphics2D object to get a
		// FontMetrics to use to calculate optimal column widths in the
		// CellSetViewer. If done before, the Graphics2D object is null.
		if (currentEditorPanel instanceof OlapQueryPanel) {
			try {
                ((OlapQueryPanel) currentEditorPanel).updateCellSet(((OlapQuery) entryPanelModel).execute());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
		}
		// TODO Select the proper panel in the wabit tree
		return true;
	}
	
	/**
	 * This will close the editor panel the user is currently modifying if 
	 * the user has no changes or discards their changes. This will return true
	 * if the panel was properly closed or false if it was not closed (ie: due to
	 * unsaved changes).
	 */
	private boolean removeEditorPanel() {
		if (currentEditorPanel != null && currentEditorPanel.hasUnsavedChanges()) {
			int retval = JOptionPane.showConfirmDialog(frame, "There are unsaved changes. Discard?", "Discard changes", JOptionPane.YES_NO_OPTION);
			if (retval == JOptionPane.NO_OPTION) {
				return false;
			}
		}
		if (currentEditorPanel != null) {
			if (currentEditorPanel instanceof QueryPanel) {
				QueryPanel query = (QueryPanel)currentEditorPanel;
				prefs.put(QUERY_DIVIDER_LOCATON, String.format("%d,%d", query.getTopRightSplitPane().getDividerLocation(), query.getFullSplitPane().getDividerLocation()));
			} else if (currentEditorPanel instanceof ReportLayoutPanel) {
				prefs.put(LAYOUT_DIVIDER_LOCATION, String.format("%d", ((ReportLayoutPanel) currentEditorPanel).getSplitPane().getDividerLocation()));
			}
			currentEditorPanel.discardChanges();
		}
		return true;
	}
	
	public DatabaseConnectionManager getDbConnectionManager() {
		return dbConnectionManager;
	}
	
	private boolean hasUnsavedChanges() {
		if (currentFile == null) {
			return workspace.getChildren().size() > 0;
		}
		
		// FIXME: this does not work, because some GUIDs change every time you load/save.
		// It would be much better to track changes to the model using a listener.
		
		ByteArrayOutputStream currentWorkspaceOutStream = new ByteArrayOutputStream();
		new WorkspaceXMLDAO(currentWorkspaceOutStream, workspace).save();
		
		BufferedReader existingWorkspaceFile = null;
		try {
		    String currentWorkspaceAsString = currentWorkspaceOutStream.toString("utf-8");
		    StringBuilder existingWorkspaceBuffer = new StringBuilder(currentWorkspaceAsString.length());
		    existingWorkspaceFile = new BufferedReader(new FileReader(currentFile));
		    for (;;) {
		        int nextChar = existingWorkspaceFile.read();
		        if (nextChar == -1) break;
		        existingWorkspaceBuffer.append((char) nextChar);
		    }
		    
            return !existingWorkspaceBuffer.toString().equals(currentWorkspaceAsString);
            
		} catch (IOException e) {
		    throw new RuntimeException(e);
		} finally {
		    try {
		        if (existingWorkspaceFile != null) existingWorkspaceFile.close();
		    } catch (IOException ex) {
		        logger.warn("Couldn't close comparative input file. Squishing this exception:", ex);
		    }
		}
	}
	
	public void setCurrentFile(File savedOrLoadedFile) {
		this.currentFile = savedOrLoadedFile;
	}
	
	public File getCurrentFile() {
		return currentFile;
	}
	
	public UserPrompter createUserPrompter(String question, UserPromptType responseType, UserPromptOptions optionType, UserPromptResponse defaultResponseType, Object defaultResponse, String ...buttonNames) {
		return upfMissingLoadedDB.createUserPrompter(question, responseType, optionType, defaultResponseType, defaultResponse, buttonNames);
	}

	public JSpinner getRowLimitSpinner() {
		return rowLimitSpinner;
	}

	public int getRowLimit() {
		return (Integer) rowLimitSpinner.getValue();
	}

	public void addPropertyChangeListener(PropertyChangeListener l) {
		pcs.addPropertyChangeListener(l);
	}

	public void removePropertyChangeListener(PropertyChangeListener l) {
		pcs.removePropertyChangeListener(l);
	}
	
	public boolean isLoading() {
        return loading;
    }
	
	public void setLoading(boolean loading) {
        this.loading = loading;
    }

    public Connection borrowConnection(JDBCDataSource dataSource) throws SQLObjectException {
        return getDatabase(dataSource).getConnection();
    }

    public SQLDatabase getDatabase(JDBCDataSource dataSource) {
    	if (dataSource == null) return null;
        SQLDatabase db = databases.get(dataSource);
        if (db == null) {
            dataSource = new JDBCDataSource(dataSource);  // defensive copy for cache key
            db = new SQLDatabase(dataSource);
            databases.put(dataSource, db);
        }
        return db;
    }
    
    public OlapConnection createConnection(Olap4jDataSource dataSource) 
    throws SQLException, ClassNotFoundException, NamingException {
        if (dataSource == null) return null;
        OlapConnectionPool olapConnectionPool = olapConnectionPools.get(dataSource);
        if (olapConnectionPool == null) {
            olapConnectionPool = new OlapConnectionPool(dataSource, this);
            olapConnectionPools.put(dataSource, olapConnectionPool);
        }
        return olapConnectionPool.getConnection();
    }
}