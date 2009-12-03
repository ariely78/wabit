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

package ca.sqlpower.wabit.rs;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.annotation.Nonnull;

import net.jcip.annotations.GuardedBy;

import org.apache.log4j.Logger;

import ca.sqlpower.sql.CachedRowSet;
import ca.sqlpower.wabit.AbstractWabitObject;
import ca.sqlpower.wabit.WabitSession;
import ca.sqlpower.wabit.rs.query.QueryCache;
import ca.sqlpower.wabit.swingui.ExceptionHandler;

/**
 * Contains a set of result sets and update counts that are returned by
 * executing a query.
 */
public class ResultSetAndUpdateCountCollection {
    
    private static final Logger logger = Logger.getLogger(ResultSetAndUpdateCountCollection.class);

    /**
     * This CachedRowSet extension fires events on the foreground thread of the
     * {@link QueryCache} that made its parent.
     */
    private class CachedRowSetWithForegroundEvents extends CachedRowSet {
        
        public CachedRowSetWithForegroundEvents() throws SQLException {
            super();
        }

        @Override
        protected void fireRowAdded(final Object[] row, final int rowNum) {
            session.runInForeground(new Runnable() {
                public void run() {
                    CachedRowSetWithForegroundEvents.super.fireRowAdded(row, rowNum);
                }
            });
        }
    }
    
    /**
     * This will contain a {@link CachedRowSet} for each result set in the
     * statement passed into the constructor. This list will contain a null
     * entry in each position where the statement returns an update count
     * but there is no result set associated for it as it was not a query.
     */
    private final List<CachedRowSet> cachedRowSets = new ArrayList<CachedRowSet>();
    
    /**
     * Contains an entry for each update count in the statement given to the
     * constructor. These update counts will be in the order they are received
     * from the statement.
     */
    private final List<Integer> updateCounts = new ArrayList<Integer>();
    
    /**
     * All of the threads used in streaming result sets are stored here. This
     * allows the cleanup to stop the streaming threads during cleanup. The
     * streaming of result sets are done on different threads instead of using
     * the {@link WabitSession#runInBackground(Runnable)} method because we must
     * guarantee that the streaming is done on a separate thread. If the streaming
     * was not done in a separate thread and the background thread was the same 
     * as the foreground thread the application would block on the streaming
     * and never return for some streaming queries.
     */
    private final List<Thread> streamingThreads = new ArrayList<Thread>();

    /**
     * This counts the number of streaming queries that are still streaming
     * results in this collection.
     */
    private int activeStreamingQueryCount = 0;
    
    /**
     * All of the listeners will be notified when all of the streaming result
     * sets in this collection have stopped.
     */
    @GuardedBy("resultSetListeners")
    private final List<StreamingResultSetCollectionListener> resultSetListeners = new 
        ArrayList<StreamingResultSetCollectionListener>();

    /**
     * This {@link AbstractWabitObject} is used to fire events on the proper
     * foreground thread from the cached row sets and this class itself. 
     * If a WabitObject needs to be a parent of this collection and does not
     * extend {@link AbstractWabitObject} refactor the runInBackground and
     * runInForeground methods to a more central place.
     */
    private final WabitSession session;

	/**
	 * This constructor will iterate through the statement's results and collect
	 * a set of result sets and update counts. If the result sets in this
	 * statement are not streaming then the result sets stored in this class
	 * will not need any database resources and the statement can be closed
	 * after this object has been constructed. If the result sets are streaming
	 * the statement will have to remain open to continue streaming entries into
	 * the stored result sets.
	 * <p>
	 * If the {@link CachedRowSet} can be set to unmodifiable in the future the
	 * {@link CachedRowSet}s created in this class should be unmodifiable.
	 * 
	 * @param statement
	 *            The statement to get results from.
	 * @param isNextAResultSet
	 *            True if the first result from the statement is a result set.
	 *            False if the first result from the statement is an update
	 *            count.
	 * @param isStreaming
	 *            True if the result sets in this statement are streaming, false
	 *            otherwise.
	 * @param streamingRowLimit
	 *            The number of rows to retain in the result set that is
	 *            streaming values.
	 * @param WabitSession
	 *            Uses the session to fire events in the appropriate foreground
	 *            thread
	 */
    public ResultSetAndUpdateCountCollection(Statement statement, boolean isNextAResultSet, 
            boolean isStreaming, final int streamingRowLimit, final WabitSession session) throws SQLException {
    	this.session = session;
        boolean hasNext = true;
        while (hasNext) {
            if (isNextAResultSet) {
                final CachedRowSet crs = new CachedRowSetWithForegroundEvents();
                if (isStreaming) {
                    final ResultSet streamingRS = statement.getResultSet();
                    Thread t = new Thread() {
                        @Override
                        public void run() {
                            try {
                                Thread.currentThread().setUncaughtExceptionHandler(new ExceptionHandler());
                                crs.follow(streamingRS, streamingRowLimit);
                            } catch (SQLException e) {
                                logger.error("Exception while streaming result set", e);
                            } finally {
                                activeStreamingQueryCount--;
                                if (activeStreamingQueryCount == 0) {
                                    session.runInForeground(new Runnable() {
                                        public void run() {
                                            fireAllStreamingStopped();
                                        }
                                    });
                                }
                            }
                        }
                    };
                    t.start();
                    streamingThreads.add(t);
                    activeStreamingQueryCount++;
                } else {
                    crs.populate(statement.getResultSet());
                }
                cachedRowSets.add(crs);
            } else {
                cachedRowSets.add(null);
            }
            updateCounts.add(statement.getUpdateCount());
            isNextAResultSet = statement.getMoreResults();
            hasNext = !((isNextAResultSet == false) && (statement.getUpdateCount() == -1));
        }
    }

    /**
     * This will wrap a given result set in a
     * {@link ResultSetAndUpdateCountCollection}. The result set stored will be
     * scrollable and contain no resources back to the original result set.
     * 
     * @param rs
     *            The result set to wrap. This cannot be a streaming result set.
     * @param session
     *            The WabitSession to fire events on an appropriate foreground
     *            thread.
     * @throws SQLException
     *             Thrown if there are problems iterating over the result set.
     */
    public ResultSetAndUpdateCountCollection(ResultSet rs, WabitSession session) 
            throws SQLException {
    	this.session = session;
        if (rs instanceof CachedRowSet) {
            cachedRowSets.add((CachedRowSet) rs);
        } else if (rs != null) {
            CachedRowSet crs;
            crs = new CachedRowSet();
            crs.populate(rs);
            cachedRowSets.add(crs);
        } else {
            cachedRowSets.add(null);
        }
    }

    /**
     * Returns a copy of the given result set collection. The cached row sets
     * contained in the copy will be shared copies of the ones in the given
     * collection. The referenced threads in the copy will reference the threads
     * in the original collection which means stopping the threads in a copy of
     * a collection will stop the threads in all of the copies and the original
     * collection.
     */
    public ResultSetAndUpdateCountCollection(
            ResultSetAndUpdateCountCollection rsCollection) {
        updateCounts.addAll(rsCollection.updateCounts);
        for (CachedRowSet crs : rsCollection.cachedRowSets) {
            if (crs != null) {
                try {
                    cachedRowSets.add(crs.createShared());
                } catch (SQLException e) {
                    throw new RuntimeException("Exception when creating a shared copy " +
                            "of a cached row set.", e);
                }
            } else {
                cachedRowSets.add(null);
            }
        }
        streamingThreads.addAll(rsCollection.streamingThreads);
        this.session = rsCollection.session;
    }

    /**
     * The added listener will be notified when all of the streaming queries have stopped.
     * This cannot be null.
     */
    public void addResultSetListener(@Nonnull StreamingResultSetCollectionListener l) {
        synchronized(resultSetListeners) {
            resultSetListeners.add(l);
        }
    }

    /**
     * The removed listener will no longer be notified when all of the streaming
     * queries have stopped streaming in this collection.
     */
    public void removeResultSetListener(StreamingResultSetCollectionListener l) {
        synchronized(resultSetListeners) {
            resultSetListeners.remove(l);
        }
    }
    
    /**
     * This will fire an event to all listeners notifying them that all of the streaming
     * queries in this collection have stopped streaming.
     */
    private void fireAllStreamingStopped() {
        final StreamingResultSetCollectionEvent evt = new StreamingResultSetCollectionEvent(this);
        synchronized(resultSetListeners) {
            for (int i = resultSetListeners.size() - 1; i >= 0; i--) {
                resultSetListeners.get(i).allStreamingStopped(evt);
            }
        }
    }
    
    /**
     * Gets the first result set from the collection of result sets. This
     * can be null if an update count was the first value returned by the
     * statement and no result set was associated with it. Throws an index
     * out of bounds exception if there are no result sets in this collection.
     */
    public CachedRowSet getFirstResultSet() {
        return cachedRowSets.get(0);
    }
    
    /**
     * Returns the first non-null result set contained in this collection.
     * If there are no non-null result sets in this collection it will return
     * null. The result set returned will be a shared copy of the result
     * set returned.
     * @see CachedRowSet#createShared()
     */
    public CachedRowSet getFirstNonNullResultSet() {
        for (CachedRowSet crs : cachedRowSets) {
            if (crs != null) {
                try {
                    return crs.createShared();
                } catch (SQLException e) {
                    throw new AssertionError("This should not be possible");
                }
            } 
        }
        return null;
    }
    
    /**
     * Returns the number of null and non-null result set entries in this collection.
     */
    public int getResultSetCount() {
        return cachedRowSets.size();
    }
    
    /**
     * Gets all of the result sets contained by this collection. This list will be 
     * unmodifiable.
     */
    public List<CachedRowSet> getResultSets() {
        return Collections.unmodifiableList(cachedRowSets);
    }
    
    /**
     * Returns the number of update counts in this collection.
     */
    public int getCountOfUpdateCounts() {
        return updateCounts.size();
    }
    
    /**
     * Returns an unmodifiable list of the update counts.
     */
    public List<Integer> getUpdateCounts() {
        return Collections.unmodifiableList(updateCounts);
    }
    
    public void cleanup() throws SQLException {
        for (Thread t : streamingThreads) {
            t.interrupt();
        }
    }
}