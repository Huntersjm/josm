// License: GPL. See LICENSE file for details.
package org.openstreetmap.josm.data.validation.tests;

import static org.openstreetmap.josm.tools.I18n.tr;

import java.awt.geom.Area;
import java.awt.geom.Line2D;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.openstreetmap.josm.Main;
import org.openstreetmap.josm.data.coor.EastNorth;
import org.openstreetmap.josm.data.coor.LatLon;
import org.openstreetmap.josm.data.osm.BBox;
import org.openstreetmap.josm.data.osm.Node;
import org.openstreetmap.josm.data.osm.OsmUtils;
import org.openstreetmap.josm.data.osm.QuadBuckets;
import org.openstreetmap.josm.data.osm.Way;
import org.openstreetmap.josm.data.validation.Severity;
import org.openstreetmap.josm.data.validation.Test;
import org.openstreetmap.josm.data.validation.TestError;
import org.openstreetmap.josm.gui.preferences.ValidatorPreference;
import org.openstreetmap.josm.gui.progress.ProgressMonitor;

/**
 * Tests if there are segments that crosses in the same layer
 *
 * @author frsantos
 */
public class UnconnectedWays extends Test {

    protected static final int UNCONNECTED_WAYS = 1301;
    protected static final String PREFIX = ValidatorPreference.PREFIX + "." + UnconnectedWays.class.getSimpleName();

    private Set<MyWaySegment> ways;
    private QuadBuckets<Node> endnodes; // nodes at end of way
    private QuadBuckets<Node> endnodes_highway; // nodes at end of way
    private QuadBuckets<Node> middlenodes; // nodes in middle of way
    private Set<Node> othernodes; // nodes appearing at least twice
    private Area dsArea;

    private double mindist;
    private double minmiddledist;

    /**
     * Constructor
     */
    public UnconnectedWays() {
        super(tr("Unconnected ways"),
                tr("This test checks if a way has an endpoint very near to another way."));
    }

    @Override
    public void startTest(ProgressMonitor monitor) {
        super.startTest(monitor);
        ways = new HashSet<MyWaySegment>();
        endnodes = new QuadBuckets<Node>();
        endnodes_highway = new QuadBuckets<Node>();
        middlenodes = new QuadBuckets<Node>();
        othernodes = new HashSet<Node>();
        mindist = Main.pref.getDouble(PREFIX + ".node_way_distance", 10.0);
        minmiddledist = Main.pref.getDouble(PREFIX + ".way_way_distance", 0.0);
        dsArea = Main.main.getCurrentDataSet().getDataSourceArea();
    }

    @Override
    public void endTest() {
        Map<Node, Way> map = new HashMap<Node, Way>();
        for (int iter = 0; iter < 1; iter++) {
            for (MyWaySegment s : ways) {
                Collection<Node> nearbyNodes = s.nearbyNodes(mindist);
                for (Node en : nearbyNodes) {
                    if (en == null || !s.highway || !endnodes_highway.contains(en)) {
                        continue;
                    }
                    if ("turning_circle".equals(en.get("highway"))
                            || "bus_stop".equals(en.get("highway"))
                            || "buffer_stop".equals(en.get("railway"))
                            || OsmUtils.isTrue(en.get("noexit"))
                            || en.hasKey("barrier")) {
                        continue;
                    }
                    // There's a small false-positive here.  Imagine an intersection
                    // like a 't'.  If the top part of the 't' is short enough, it
                    // will trigger the node at the very top of the 't' to be unconnected
                    // to the way that "crosses" the 't'.  We should probably check that
                    // the ways to which 'en' belongs are not connected to 's.w'.
                    map.put(en, s.w);
                }
                if(isCanceled())
                    return;
            }
        }
        for (Map.Entry<Node, Way> error : map.entrySet()) {
            errors.add(new TestError(this, Severity.WARNING,
                    tr("Way end node near other highway"),
                    UNCONNECTED_WAYS,
                    Arrays.asList(error.getKey(), error.getValue()),
                    Arrays.asList(error.getKey())));
        }
        map.clear();
        for (MyWaySegment s : ways) {
            if(isCanceled())
                return;
            for (Node en : s.nearbyNodes(mindist)) {
                if (endnodes_highway.contains(en) && !s.highway && !s.w.concernsArea()) {
                    map.put(en, s.w);
                } else if (endnodes.contains(en) && !s.w.concernsArea()) {
                    map.put(en, s.w);
                }
            }
        }
        for (Map.Entry<Node, Way> error : map.entrySet()) {
            errors.add(new TestError(this, Severity.WARNING,
                    tr("Way end node near other way"),
                    UNCONNECTED_WAYS,
                    Arrays.asList(error.getKey(), error.getValue()),
                    Arrays.asList(error.getKey())));
        }
        /* the following two use a shorter distance */
        if (minmiddledist > 0.0) {
            map.clear();
            for (MyWaySegment s : ways) {
                if(isCanceled())
                    return;
                for (Node en : s.nearbyNodes(minmiddledist)) {
                    if (!middlenodes.contains(en)) {
                        continue;
                    }
                    map.put(en, s.w);
                }
            }
            for (Map.Entry<Node, Way> error : map.entrySet()) {
                errors.add(new TestError(this, Severity.OTHER,
                        tr("Way node near other way"),
                        UNCONNECTED_WAYS,
                        Arrays.asList(error.getKey(), error.getValue()),
                        Arrays.asList(error.getKey())));
            }
            map.clear();
            for (MyWaySegment s : ways) {
                for (Node en : s.nearbyNodes(minmiddledist)) {
                    if(isCanceled())
                        return;
                    if (!othernodes.contains(en)) {
                        continue;
                    }
                    map.put(en, s.w);
                }
            }
            for (Map.Entry<Node, Way> error : map.entrySet()) {
                errors.add(new TestError(this, Severity.OTHER,
                        tr("Connected way end node near other way"),
                        UNCONNECTED_WAYS,
                        Arrays.asList(error.getKey(), error.getValue()),
                        Arrays.asList(error.getKey())));
            }
        }
        ways = null;
        endnodes = null;
        endnodes_highway = null;
        middlenodes = null;
        othernodes = null;
        dsArea = null;
        super.endTest();
    }

    private class MyWaySegment {
        private final Line2D line;
        public final Way w;
        public final boolean isAbandoned;
        public final boolean isBoundary;
        public final boolean highway;
        private final double len;
        private Set<Node> nearbyNodeCache;
        double nearbyNodeCacheDist = -1.0;
        final Node n1;
        final Node n2;

        public MyWaySegment(Way w, Node n1, Node n2) {
            this.w = w;
            String railway = w.get("railway");
            String highway = w.get("highway");
            this.isAbandoned = "abandoned".equals(railway) || OsmUtils.isTrue(w.get("disused"));
            this.highway = (highway != null || railway != null) && !isAbandoned;
            this.isBoundary = !this.highway && "administrative".equals(w.get("boundary"));
            line = new Line2D.Double(n1.getEastNorth().east(), n1.getEastNorth().north(),
                    n2.getEastNorth().east(), n2.getEastNorth().north());
            len = line.getP1().distance(line.getP2());
            this.n1 = n1;
            this.n2 = n2;
        }

        public boolean nearby(Node n, double dist) {
            if (w == null) {
                Main.debug("way null");
                return false;
            }
            if (w.containsNode(n))
                return false;
            if (OsmUtils.isTrue(n.get("noexit")))
                return false;
            EastNorth coord = n.getEastNorth();
            if (coord == null)
                return false;
            Point2D p = new Point2D.Double(coord.east(), coord.north());
            if (line.getP1().distance(p) > len+dist)
                return false;
            if (line.getP2().distance(p) > len+dist)
                return false;
            return line.ptSegDist(p) < dist;
        }

        public List<LatLon> getBounds(double fudge) {
            double x1 = n1.getCoor().lon();
            double x2 = n2.getCoor().lon();
            if (x1 > x2) {
                double tmpx = x1;
                x1 = x2;
                x2 = tmpx;
            }
            double y1 = n1.getCoor().lat();
            double y2 = n2.getCoor().lat();
            if (y1 > y2) {
                double tmpy = y1;
                y1 = y2;
                y2 = tmpy;
            }
            LatLon topLeft  = new LatLon(y2+fudge, x1-fudge);
            LatLon botRight = new LatLon(y1-fudge, x2+fudge);
            List<LatLon> ret = new ArrayList<LatLon>(2);
            ret.add(topLeft);
            ret.add(botRight);
            return ret;
        }

        public Collection<Node> nearbyNodes(double dist) {
            // If you're looking for nodes that are farther
            // away that we looked for last time, the cached
            // result is no good
            if (dist > nearbyNodeCacheDist) {
                nearbyNodeCache = null;
            }
            if (nearbyNodeCache != null) {
                // If we've cached an aread greater than the
                // one now being asked for...
                if (nearbyNodeCacheDist > dist) {
                    // Used the cached result and trim out
                    // the nodes that are not in the smaller
                    // area, but keep the old larger cache.
                    Set<Node> trimmed = new HashSet<Node>(nearbyNodeCache);
                    Set<Node> initial = new HashSet<Node>(nearbyNodeCache);
                    for (Node n : initial) {
                        if (!nearby(n, dist)) {
                            trimmed.remove(n);
                        }
                    }
                    return trimmed;
                }
                return nearbyNodeCache;
            }
            /*
             * We know that any point near the line must be at
             * least as close as the other end of the line, plus
             * a little fudge for the distance away ('dist').
             */

            // This needs to be a hash set because the searches
            // overlap a bit and can return duplicate nodes.
            nearbyNodeCache = null;
            List<LatLon> bounds = this.getBounds(dist);
            List<Node> found_nodes = endnodes_highway.search(new BBox(bounds.get(0), bounds.get(1)));
            found_nodes.addAll(endnodes.search(new BBox(bounds.get(0), bounds.get(1))));

            for (Node n : found_nodes) {
                if (!nearby(n, dist) || !n.getCoor().isIn(dsArea)) {
                    continue;
                }
                // It is actually very rare for us to find a node
                // so defer as much of the work as possible, like
                // allocating the hash set
                if (nearbyNodeCache == null) {
                    nearbyNodeCache = new HashSet<Node>();
                }
                nearbyNodeCache.add(n);
            }
            nearbyNodeCacheDist = dist;
            if (nearbyNodeCache == null) {
                nearbyNodeCache = Collections.emptySet();
            }
            return nearbyNodeCache;
        }
    }

    List<MyWaySegment> getWaySegments(Way w) {
        List<MyWaySegment> ret = new ArrayList<MyWaySegment>();
        if (!w.isUsable()
                || w.hasKey("barrier")
                || "cliff".equals(w.get("natural")))
            return ret;

        int size = w.getNodesCount();
        if (size < 2)
            return ret;
        for (int i = 1; i < size; ++i) {
            if(i < size-1) {
                addNode(w.getNode(i), middlenodes);
            }
            Node a = w.getNode(i-1);
            Node b = w.getNode(i);
            if (a.isDrawable() && b.isDrawable()) {
                MyWaySegment ws = new MyWaySegment(w, a, b);
                if (ws.isBoundary || ws.isAbandoned) {
                    continue;
                }
                ret.add(ws);
            }
        }
        return ret;
    }

    @Override
    public void visit(Way w) {
        // Do not consider empty ways and addr:interpolation ways as they are not physical features and most of the time
        // very near the associated highway, which is perfectly normal, see #9332
        if (w.getNodesCount() > 0 && !w.hasKey("addr:interpolation")) {
            ways.addAll(getWaySegments(w));
            QuadBuckets<Node> set = endnodes;
            if (w.hasKey("highway") || w.hasKey("railway")) {
                set = endnodes_highway;
            }
            addNode(w.firstNode(), set);
            addNode(w.lastNode(), set);
        }
    }

    @Override
    public void visit(Node n) {
    }

    private void addNode(Node n, QuadBuckets<Node> s) {
        boolean m = middlenodes.contains(n);
        boolean e = endnodes.contains(n);
        boolean eh = endnodes_highway.contains(n);
        boolean o = othernodes.contains(n);
        if (!m && !e && !o && !eh) {
            s.add(n);
        } else if (!o) {
            othernodes.add(n);
            if (e) {
                endnodes.remove(n);
            } else if (eh) {
                endnodes_highway.remove(n);
            } else {
                middlenodes.remove(n);
            }
        }
    }
}
