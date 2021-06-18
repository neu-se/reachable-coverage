# Comparing JaCoCo coverage with static reachability information

This utility uses Soot to create a (static) callgraph that contains all methods that can be reached from a given entrypoint (e.g. test case). Using those results, it can then analyze a JaCoCo execution report and calculate line coverage as a ratio of only those lines that are reachable in that call-graph, rather than as a ratio of *all* lines.

Notes: The call graph is calculated using the `SPARK` algorithm. The call graph does not contain methods that are reachable only through reflection. However, we'll notice that the call graph is missing things when we analyze the coverage data: if we find that a statement was covered that did not appear in the static call graph, we add the entire method containing that statement as "reachable" for reporting purposes.

## Usage

1. Compile project (`mvn package`)
1. Collect reachability information. `java -cp target/static-reachability-1.0-SNAPSHOT.jar edu.neu.ccs.se.reachability.entry.ReachabilityFinder testClassName testMethodName classPath > reachable-methods.txt`
1. Analyze JaCoCo file along with that reachabiltiy info. `java -cp target/static-reachability-1.0-SNAPSHOT.jar neu.ccs.se.reachability.entry.JaCoCoReachabilityReporter classpath jacocoExecFile reachabilityFile` 

Be sure that the classpath specified for both utilities is the same *and* that it matches the classpath from the experiment that you ran to collect the JaCoCo result file.

