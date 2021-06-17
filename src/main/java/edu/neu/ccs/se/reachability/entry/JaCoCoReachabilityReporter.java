package edu.neu.ccs.se.reachability.entry;

import org.jacoco.core.analysis.*;
import org.jacoco.core.internal.analysis.StringPool;
import org.jacoco.core.tools.ExecFileLoader;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Scanner;

public class JaCoCoReachabilityReporter {
    public static void main(String[] args) throws IOException {
        if (args.length != 3) {
            System.err.println("Usage: java edu.neu.ccs.se.reachability.entry.JaCoCoReachabilityReporter classpath jacocoExecFile reachabilityFile");
            return;
        }
        reportCoverage(args[1], args[0], args[2]);
    }

    static int linesCoverableBySoot = 0;
    static int linesCoveredBySoot = 0;
    static int linesCoveredTotal = 0;
    static int linesCoverableTotal = 0;

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

        final StringPool stringPool = new StringPool();

        ExecFileLoader loader = new ExecFileLoader();
        final CoverageBuilder builder = new CoverageBuilder();
        loader.load(new BufferedInputStream(new FileInputStream(jacocoExecFile)));
        Analyzer analyzer = new Analyzer(loader.getExecutionDataStore(), new ICoverageVisitor() {

            @Override
            public void visitCoverage(IClassCoverage iClassCoverage) {
                if (coverableLines.containsKey(iClassCoverage.getName())) {
                    int coverableLinesJacoco = 0;
                    int coverableLinesSoot = 0;
                    int coveredLinesSoot = 0;
                    int coveredLinesNotSoot = 0;
                    for (int i = iClassCoverage.getFirstLine(); i <= iClassCoverage.getLastLine(); i++) {
                        int lineStatus = iClassCoverage.getLine(i).getStatus();
                        if (lineStatus == ICounter.EMPTY)
                            continue;
                        boolean isReachableBySoot = coverableLines.get(iClassCoverage.getName()).contains(i);
                        linesCoverableTotal++;
                        if (isReachableBySoot) {
                            linesCoverableBySoot++;
                            coverableLinesSoot++;
                        } else {
                            coverableLinesJacoco++;
                        }
                        if (lineStatus == ICounter.FULLY_COVERED || lineStatus == ICounter.PARTLY_COVERED) {
                            linesCoveredTotal++;
                            if (isReachableBySoot) {
                                coveredLinesSoot++;
                                linesCoveredBySoot++;
                            } else {
                                System.out.println("Covered by test but not soot: " + iClassCoverage.getName() + ":" + i);
                                coveredLinesNotSoot++;
                            }
                        }
                    }
                    System.out.println(iClassCoverage.getName() + ":" + coveredLinesSoot + "/" + coverableLinesSoot + " (" + coveredLinesNotSoot + "/" + coverableLinesJacoco + ")");
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
        System.out.format("Total coverage: %d/%d (%.2f%%)\n", linesCoveredTotal, linesCoverableTotal, (100d * linesCoveredTotal) / linesCoverableTotal);
        System.out.format("Reachable coverage: %d/%d (%.2f%%)\n", linesCoveredBySoot, linesCoverableBySoot, (100d * linesCoveredBySoot) / linesCoverableBySoot);


    }
}
