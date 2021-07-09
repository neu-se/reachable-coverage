package edu.neu.ccs.se.reachability.entry;

import org.jacoco.core.analysis.*;
import org.jacoco.core.tools.ExecFileLoader;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Scanner;

public class DifferentialJaCoCoReachabilityReporter {
    public static void main(String[] args) throws IOException {
        if (args.length != 3) {
            System.err.println("Usage: java edu.neu.ccs.se.reachability.entry.JaCoCoReachabilityReporter classpath jacocoExecFile reachabilityFile");
            return;
        }
        reportCoverage(args[1], args[0], args[2]);
    }

    static int linesCovered = 0;
    static int linesCoverable = 0;
    static int linesTotal = 0;

    public static void reportCoverage(String jacocoExecFile, String cp, String reachabilityInfo) throws IOException {

        HashMap<String, HashSet<Integer>> coverableLines = new HashMap<>();
        Scanner rdr = new Scanner(new File(reachabilityInfo));
        while (rdr.hasNextLine()) {
            String line = rdr.nextLine();
            String[] data = line.split("\t");
            String className = data[0].replace('.', '/');
            int lineNumber = Integer.parseInt(data[2]);
            if (!coverableLines.containsKey(className)) {
                coverableLines.put(className, new HashSet<>());
            }
            coverableLines.get(className).add(lineNumber);
        }
        rdr.close();

        ExecFileLoader loader = new ExecFileLoader();
        final CoverageBuilder builder = new CoverageBuilder();
        loader.load(new BufferedInputStream(new FileInputStream(jacocoExecFile)));
        Analyzer analyzer = new Analyzer(loader.getExecutionDataStore(), new ICoverageVisitor() {

            @Override
            public void visitCoverage(IClassCoverage iClassCoverage) {
                if (coverableLines.containsKey(iClassCoverage.getName()) &&
                iClassCoverage.getName().startsWith("org/apache/maven/model")) {
                    for (IMethodCoverage iMethodCoverage : iClassCoverage.getMethods()) {
                        boolean addedAllLinesAsCoverable = false;
                        if (iMethodCoverage.containsCode()) {

                            for (int i = iMethodCoverage.getFirstLine(); i <= iMethodCoverage.getLastLine(); i++) {
                                int lineStatus = iMethodCoverage.getLine(i).getStatus();
                                if (lineStatus == ICounter.EMPTY)
                                    continue;
                                linesTotal++;
                                boolean isReachableBySoot = coverableLines.get(iClassCoverage.getName()).contains(i);
                                if (lineStatus == ICounter.FULLY_COVERED || lineStatus == ICounter.PARTLY_COVERED) {
                                    linesCovered++;
                                    if (isReachableBySoot) {
                                        linesCoverable++;
                                    } else if (!addedAllLinesAsCoverable) {
                                        addedAllLinesAsCoverable = true;
                                        for(int j = iMethodCoverage.getFirstLine(); j<= iMethodCoverage.getLastLine(); j++){
                                            int status = iMethodCoverage.getLine(j).getStatus();
                                            if(status != ICounter.EMPTY && !coverableLines.get(iClassCoverage.getName()).contains(j)){
                                                System.out.println("Covered by test but not soot: " + iClassCoverage.getName() + ":" + j);
                                                linesCoverable++;
                                            }
                                        }
                                    }
                                } else if (isReachableBySoot) {
                                    linesCoverable++;
                                }
                            }
                        }
                    }
                }
            }
        });
        for (String path : cp.split(":")) {
            try {
                analyzer.analyzeAll(new File(path));
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        }
        System.out.format("Final Reachable coverage: %d/%d (%.2f%%)\n", linesCovered, linesCoverable, (100d * linesCovered) / linesCoverable);
        System.out.format("Coverage considering all statements: %d/%d (%.2f%%)\n", linesCovered, linesTotal, (100d * linesCovered) / linesTotal);



    }
}
