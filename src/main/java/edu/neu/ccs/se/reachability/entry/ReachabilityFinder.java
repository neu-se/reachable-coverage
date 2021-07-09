package edu.neu.ccs.se.reachability.entry;

import java.io.File;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.*;

import org.apache.commons.io.output.NullPrintStream;
import soot.*;
import soot.jimple.toolkits.callgraph.CHATransformer;
import soot.jimple.toolkits.callgraph.CallGraph;
import soot.jimple.toolkits.callgraph.ReachableMethods;
import soot.jimple.toolkits.callgraph.Targets;
import soot.options.Options;
import soot.tagkit.Tag;
import soot.util.queue.QueueReader;

public class ReachabilityFinder {
    public static void main(String[] args) {
        if(args.length != 3){
            System.err.println("Usage: java edu.neu.ccs.se.reachability.entry.ReachabilityFinder entryClassName entryMethodName classpath");
            return;
        }

        //Soot has a println of "Found %d instanceinvoke , %d staticinvoke  edge descriptions
        //This seems to be the only way to disable that :'(
        PrintStream originalSystemOut = System.out;
        System.setOut(new NullPrintStream());

        ArrayList<String> reachable = findStaticallyReachableLines(args[0], args[1], args[2]);

        System.setOut(originalSystemOut);
        for(String s : reachable){
            System.out.println(s);
        }

    }

    public static ArrayList<String> findStaticallyReachableLines(String entryClass, String entryMethod, String classPath){
        String jredir = System.getProperty("java.home")+"/lib/rt.jar";
        String path = File.pathSeparator+jredir+File.pathSeparator+classPath;
        Options.v().set_soot_classpath(path);

        final List<String> excludePackagesList = Arrays.asList("java.","sun.", "com.sun.", "jdk.", "javax.");

        Options.v().set_whole_program(true);
        Options.v().set_allow_phantom_refs(true);
        Options.v().set_keep_line_number(true);
        Options.v().set_app(true);

        Options.v().setPhaseOption("cg", "safe-newinstance:true");
        Options.v().setPhaseOption("cg.cha","enabled:false");
        Options.v().setPhaseOption("cg.spark","enabled:true");
        Options.v().setPhaseOption("cg.spark","verbose:false");
        Options.v().setPhaseOption("cg.spark","on-fly-cg:true");

        final SootClass c = Scene.v().loadClass(entryClass, SootClass.BODIES);
        c.setApplicationClass();
        Scene.v().loadNecessaryClasses();

        final SootMethod entryPoint = c.getMethodByName(entryMethod);
        List<SootMethod> entryPoints = new ArrayList<SootMethod>();
        entryPoints.add(entryPoint);
        Scene.v().setEntryPoints(entryPoints);
        final HashSet<String> classesAndLines = new HashSet<String>();

        PackManager.v().getPack("wjtp").add(new Transform("wjtp.myTrans", new SceneTransformer() {

            @Override
            protected void internalTransform(String phaseName, Map options) {
                CHATransformer.v().transform();
                CallGraph cg = Scene.v().getCallGraph();

                Iterator<MethodOrMethodContext> targets = new Targets(cg.edgesOutOf(entryPoint));
                ReachableMethods reachableMethods = new ReachableMethods(cg, targets);
                QueueReader queueReader = reachableMethods.listener();
                reachableMethods.update();
                HashSet<SootMethod> reachableNonJDKMethods = new HashSet<SootMethod>();

                methodIter: while (queueReader.hasNext()) {
                    Object o = queueReader.next();
                    if(o instanceof SootMethod){
                        SootMethod m = (SootMethod) o;
                        for(String s : excludePackagesList){
                            if(m.getDeclaringClass().getName().startsWith(s))
                            {
                                continue methodIter;
                            }
                        }
                        reachableNonJDKMethods.add(m);
                    }
                }

                for(SootMethod s: reachableNonJDKMethods){
                    if(s.getJavaSourceStartLineNumber() >= 0){
                        for(Unit u : s.getActiveBody().getUnits()){
                            int lineNumber = u.getJavaSourceStartLineNumber();
                            if(lineNumber >= 0){
                                classesAndLines.add(s.getDeclaringClass().getName()+'\t'+s.getSubSignature()+'\t'+lineNumber);
                            }
                        }
                    }
                }
            }

        }));
        PackManager.v().runPacks();
        ArrayList<String> sortedHittableLines = new ArrayList<String>(classesAndLines);
        sortedHittableLines.sort(new Comparator<String>() {
            public int compare(String o1, String o2) {
                return o1.compareTo(o2);
            }
        });
        return sortedHittableLines;
    }
}
