package com.spring.rtree;

import java.util.Iterator;
import java.util.List;

import com.github.davidmoten.rtree.*;
import rx.Observable;
import rx.functions.Func1;

import com.github.davidmoten.grumpy.core.Position;
import com.github.davidmoten.rtree.geometry.Point;
import com.github.davidmoten.rtree.geometry.Rectangle;
import com.github.davidmoten.rtree.geometry.Geometries;

public class Query {

    private static final Point sydney = Geometries.point(151.2094, -33.86);
    private static final Point canberra = Geometries.point(149.1244, -35.3075);
    private static final Point brisbane = Geometries.point(153.0278, -27.4679);
    private  static RTree<String, Point> tree = RTree.star().create();


    public static void main(String args[]){

        tree = tree.add("Sydney", sydney);
        tree = tree.add("Brisbane", brisbane);
        tree = tree.add("Canberra",canberra);
        Node node = tree.root().get();
        Query query = new Query();
        query.fetchPlace(node,"Sydney");
        // Now search for all locations within 300km of Canberra
        final double distanceKm =300;
        //Observable<Entry<String,Point>> results = tree.entries();
        List<Entry<String,Point>> results = search(tree,canberra,distanceKm).toList().toBlocking().single();
        System.out.println(results.size());
        System.out.println(results.get(0));
    }

    public Point fetchPlace(Node node, String search){

        Point point=null;

        if (node instanceof NonLeaf) {
            NonLeaf<String, Point> n = (NonLeaf)node;

            for(int i = 0; i < n.count(); ++i) {
                Node<String, Point> child = n.child(i);
                this.fetchPlace(child, search);
            }
        } else {

            Leaf<String, Point> leaf = (Leaf)node;
            Iterator var8 = leaf.entries().iterator();

            while(var8.hasNext()) {
                Entry<String, Point> entry = (Entry)var8.next();
                if(entry.value().equalsIgnoreCase(search)){
                    point = entry.geometry();
                }
            }
        }
        //System.out.println(point.toString());
        return point;

    }

    public static <T> Observable<Entry<T, Point>> search(RTree<T, Point> tree, Point lonLat,
                                                         final double distanceKm) {
        // First we need to calculate an enclosing lat long rectangle for this
        // distance then we refine on the exact distance
        final Position from = Position.create(lonLat.y(), lonLat.x());
        Rectangle bounds = createBounds(from, distanceKm);

        return tree
                // do the first search using the bounds
                .search(bounds)
                // refine using the exact distance
                .filter(new Func1<Entry<T, Point>, Boolean>() {
                    public Boolean call(Entry<T, Point> entry) {
                        Point p = entry.geometry();
                        Position position = Position.create(p.y(), p.x());
                        return from.getDistanceToKm(position) < distanceKm;
                    }
                });
    }

    private static Rectangle createBounds(final Position from, final double distanceKm) {
        // this calculates a pretty accurate bounding box. Depending on the
        // performance you require you wouldn't have to be this accurate because
        // accuracy is enforced later
        Position north = from.predict(distanceKm, 0);
        Position south = from.predict(distanceKm, 180);
        Position east = from.predict(distanceKm, 90);
        Position west = from.predict(distanceKm, 270);

        return Geometries.rectangle(west.getLon(), south.getLat(), east.getLon(), north.getLat());
    }

}
