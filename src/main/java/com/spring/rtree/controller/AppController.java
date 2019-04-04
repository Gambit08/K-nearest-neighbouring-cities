package com.spring.rtree.controller;

import com.github.davidmoten.grumpy.core.Position;
import com.github.davidmoten.rtree.*;
import com.github.davidmoten.rtree.geometry.Geometries;
import com.github.davidmoten.rtree.geometry.Point;
import com.github.davidmoten.rtree.geometry.Rectangle;
import com.spring.rtree.models.Coordinate;
import com.spring.rtree.models.Query;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;

import rx.Observable;
import rx.functions.Func1;

import java.util.Iterator;
import java.util.List;

@Controller
public class AppController {

    // inject via application.properties
    @Value("${welcome.message:test}")
    private String message = "Hello World";

    private static final Point sydney = Geometries.point(151.2094, -33.86);// longitude, latitude
    private static final Point canberra = Geometries.point(149.1244, -35.3075);
    private static final Point brisbane = Geometries.point(153.0278, -27.4679);
    private static RTree<String, Point> tree = RTree.star().create();

    static{
        //adding some tree nodes
        tree = tree.add("Sydney", sydney);
        tree = tree.add("Brisbane", brisbane);
    }


    @RequestMapping(value = "/",method = RequestMethod.GET)
    public String landing(Model model, @RequestParam(value="name", required=false, defaultValue="World") String name) {
        return "Landing";
    }

    @RequestMapping(value="/service/rtree/add",method = RequestMethod.GET)
    public String home(Model model){
        model.addAttribute("coordinate", new Coordinate());
        return "Add-location";
    }

    @RequestMapping(value="/service/rtree/add",method = RequestMethod.POST)
    public String add(@ModelAttribute Coordinate coordinate){
        tree=tree.add(coordinate.getLocation(),Geometries.point(coordinate.getLongitude(),coordinate.getLatitude()));
        System.out.println(tree.entries().toList().toBlocking().single().size());
        return "Add-location";
    }

    @RequestMapping(value = "/service/rtree/query", method = RequestMethod.GET)
    public String query(Model model){
        model.addAttribute("query", new Query());
        return "Query";
    }

    @RequestMapping(value = "/service/rtree/query", method = RequestMethod.POST)
    public String location(@ModelAttribute Query query){
        Node node = tree.root().get();
        String location = query.getLocation();
        long maxDistance = query.getDistance();
        Point geometryPoint = fetchPlace(node,location);
        List<Entry<String,Point>> results = search(tree,geometryPoint,maxDistance).toList().toBlocking().single();
        printResults(results);
        return "Query";
    }

    public void printResults(List<Entry<String,Point>> results){
        System.out.println("results size: "+results.size());
        for(Entry<String,Point> result:results){
            System.out.println(result.value() + ", "+ result.geometry());
        }
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

            Leaf<String, Point> leaf = (Leaf) node;
            Iterator var8 = leaf.entries().iterator();

            while (var8.hasNext()) {
                Entry<String, Point> entry = (Entry) var8.next();
                if (entry.value().equalsIgnoreCase(search)) {
                    point = entry.geometry();
                }
            }
        }
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
                        return from.getDistanceToKm(position) < distanceKm && from.getDistanceToKm(position)!= 0;
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