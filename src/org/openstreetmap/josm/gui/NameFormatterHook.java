// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.gui;

import org.openstreetmap.josm.data.osm.INode;
import org.openstreetmap.josm.data.osm.IRelation;
import org.openstreetmap.josm.data.osm.IWay;

public interface NameFormatterHook {

    /**
     * Check the relation type name. Return the corrected type name if needed, null otherwise.
     * @param relation The relation.
     * @param defaultName The default name generated by core.
     * @return The corrected type name if needed, null otherwise.
     */
    public String checkRelationTypeName(IRelation relation, String defaultName);

    /**
     * Check the node format. Return the corrected format if needed, null otherwise.
     * @param node The node.
     * @param defaultName The default name generated by core.
     * @return The corrected format if needed, null otherwise.
     */
    public String checkFormat(INode node, String defaultName);

    /**
     * Check the way format. Return the corrected format if needed, null otherwise.
     * @param way The way.
     * @param defaultName The default name generated by core.
     * @return The corrected format if needed, null otherwise.
     */
    public String checkFormat(IWay way, String defaultName);

    /**
     * Check the relation format. Return the corrected format if needed, null otherwise.
     * @param relation The relation.
     * @param defaultName The default name generated by core.
     * @return The corrected format if needed, null otherwise.
     */
    public String checkFormat(IRelation relation, String defaultName);
}
