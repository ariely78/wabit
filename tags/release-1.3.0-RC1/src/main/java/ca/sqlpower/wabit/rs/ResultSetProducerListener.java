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

public interface ResultSetProducerListener {

	/**
	 * Gets called when a first {@link ResultSetHandle}
	 *  of this {@link ResultSetProducer}
	 * has started it's execution.
	 */
	void executionStarted(ResultSetProducerEvent evt);
    
	/**
	 * Gets called when all the distributed {@link ResultSetHandle} of this
	 * {@link ResultSetProducer} have finished running.
	 */
    void executionStopped(ResultSetProducerEvent evt);
    
    /**
     * Gets fired to notify listeners that the structure of the
     * supplied data has changed.
     */
    void structureChanged(ResultSetProducerEvent evt);
}
