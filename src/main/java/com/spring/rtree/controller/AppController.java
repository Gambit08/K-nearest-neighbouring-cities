package com.spring.rtree.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.davidmoten.grumpy.core.Position;
import com.github.davidmoten.rtree.*;
import com.github.davidmoten.rtree.geometry.Geometries;
import com.github.davidmoten.rtree.geometry.Point;
import com.github.davidmoten.rtree.geometry.Rectangle;
import com.opencsv.CSVReader;
import com.spring.rtree.models.Coordinate;
import org.springframework.ui.Model;
import org.springframework.util.ResourceUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;

import rx.Observable;
import rx.functions.Func1;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import com.spring.rtree.models.Query;
import com.spring.rtree.models.Result;


@Controller
public class AppController {

    // inject via application.properties
    @Value("${welcome.message:test}")
    private String message = "Hello World";
    private static CSVReader reader = null;

    private static final Point sydney = Geometries.point(151.2094, -33.86);// longitude, latitude
    private static final Point canberra = Geometries.point(149.1244, -35.3075);
    private static final Point brisbane = Geometries.point(153.0278, -27.4679);
    private static RTree<String, Point> tree = RTree.star().create();

    static{
        //adding some tree nodes
        tree = tree.add("Sydney", sydney);
        tree = tree.add("Brisbane", brisbane);
    }

    @RequestMapping(value = "/loadscript")
    public String loadScript(){
        System.out.println("********************************LOADING DATA****************************************");
        try {
            int count =0;
            File file = ResourceUtils.getFile("classpath:worldcities.csv");
            reader = new CSVReader(new FileReader(file));
            String [] nextLine;
            //Read one line at a time
            long startTime = System.nanoTime();
            reader.readNext(); //skip the first line
            while ((nextLine = reader.readNext()) != null)
            {
                if(count<=5){
                    System.out.println(nextLine[0]+", "+nextLine[1]+", "+nextLine[2]+", "+nextLine[3]+", "+nextLine[4]+", "+nextLine[5]+", "+nextLine[6]);
                }
                tree=tree.add(nextLine[1],Geometries.point(Double.parseDouble(nextLine[3].trim()),Double.parseDouble(nextLine[2].trim())));
                count++;
            }

            System.out.println(tree.entries().toList().toBlocking().single().size()+" Number of entries Added Successfully");
            long stopTime = System.nanoTime();
            System.out.println("Total time taken to load dataset: "+(stopTime - startTime));
        }
        catch(Exception e){
            System.out.println("Cause: "+e.getCause());
        }
        finally {
            try {
                reader.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return "Landing";
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
    public String location(@ModelAttribute Query query,Model model){
        Node node = tree.root().get();
        String location = query.getLocation();
        long maxDistance = query.getDistance();
        float latitude = query.getLatitude();
        float longitude = query.getLongitude();
        Point geometryPoint = Geometries.point(longitude,latitude);
        List<Entry<String,Point>> results = search(tree,geometryPoint,maxDistance).toList().toBlocking().single();
        ObjectMapper mapper = new ObjectMapper();
        List<Result> listOfResults = addToList(results);
        model.addAttribute("results", listOfResults);
        return "Result";
    }

    public List<Result> addToList(List<Entry<String,Point>> results){

        List<Result> listOfResults =  new ArrayList<Result>();
        for(Entry<String,Point> result:results){
            listOfResults.add(new Result(result.value(),result.geometry().y(),result.geometry().x()));
        }
        return listOfResults;
    }

    public void printResults(List<Entry<String,Point>> results){
        System.out.println("results size: "+results.size());
        for(Entry<String,Point> result:results){
            System.out.println(result.value() + ", "+ result.geometry());
        }
    }

    public Point fetchPlace(Node node, String search){

        Point point=null;
        try {
            if (node instanceof NonLeaf) {
                NonLeaf<String, Point> n = (NonLeaf) node;

                for (int i = 0; i < n.count(); ++i) {
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
        }
        catch(Exception e){
            System.out.println("Exception thrown in fetchPlace function");
            System.out.println(e.getCause());
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