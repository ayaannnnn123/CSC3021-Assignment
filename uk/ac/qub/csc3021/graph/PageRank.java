package uk.ac.qub.csc3021.graph;

// Performs the PageRank computation until convergence is reached.
public class PageRank {
    private static long edgemapTimeTotal = 0;  // Total time spent in edgemap
    private static long nonEdgemapTimeTotal = 0;  // Total time in other operations

    private static class PageRankRelax implements Relax {
        PageRankRelax(int outdeg_[], double d_, double x_[], double y_[]) {
            outdeg = outdeg_;
            d = d_;
            x = x_;
            y = y_;
        }

        public void relax(int src, int dst) {
            double w = d / (double)outdeg[src];
            y[dst] += w * x[src];
        }

        int outdeg[];
        double d;
        double x[];
        double y[];
    };

    public static double[] compute(SparseMatrix matrix) {
        long programStart = System.nanoTime();

        final int n = matrix.getNumVertices();
        double x[] = new double[n];
        double v[] = new double[n];
        double y[] = new double[n];
        final double d = 0.85;
        final double tol = 1e-7;
        final int max_iter = 100;
        final boolean verbose = true;
        double delta = 2;
        int iter = 0;

        // Initialize vectors
        for(int i=0; i < n; ++i) {
            x[i] = v[i] = 1.0 / (double)n;
            y[i] = 0;
        }

        int outdeg[] = new int[n];
        matrix.calculateOutDegree(outdeg);

        double tm_init = (double)(System.nanoTime() - programStart) * 1e-9;
        System.err.println("Initialisation: " + tm_init + " seconds");

        PageRankRelax PRrelax = new PageRankRelax(outdeg, d, x, y);
        ParallelContext context = ParallelContextHolder.get();

        System.err.println("\nStarting iterations...");
        
        while(iter < max_iter && delta > tol) {
            // Clear y[] values before edgemap
            for(int i=0; i < n; ++i) {
                y[i] = 0.0;
            }

            // 1. Measure edgemap time (parallel part)
            long beforeEdgemap = System.nanoTime();
            context.edgemap(matrix, PRrelax);
            long afterEdgemap = System.nanoTime();
            long edgemapIterTime = afterEdgemap - beforeEdgemap;
            edgemapTimeTotal += edgemapIterTime;
            
            System.err.println("DEBUG: Iteration " + iter + " edgemap time: " + 
                             (edgemapIterTime/1e9) + " seconds");

            // 2. Measure non-edgemap operations (sequential part)
            long beforeOther = System.nanoTime();
            
            double w = 1.0 - sum(y, n);
            for(int i=0; i < n; ++i) {
                y[i] += w * v[i];
            }

            delta = normdiff(x, y, n);
            
            w = 1.0 / sum(y, n);
            for(int i=0; i < n; ++i) {
                x[i] = y[i] * w;
            }
            
            long afterOther = System.nanoTime();
            nonEdgemapTimeTotal += (afterOther - beforeOther);

            if(verbose) {
                System.err.println("iteration " + iter + ": residual error=" + delta + 
                                 " xnorm=" + sum(x, n));
            }
            
            iter++;
        }

        // Calculate and print timing analysis
        double totalTime = (edgemapTimeTotal + nonEdgemapTimeTotal) / 1e9;
        double edgemapTime = edgemapTimeTotal / 1e9;
        double nonEdgemapTime = nonEdgemapTimeTotal / 1e9;

        System.out.println("\nDETAILED TIMING ANALYSIS FOR AMDAHL'S LAW:");
        System.out.printf("Total computation time: %.3f seconds\n", totalTime);
        System.out.printf("Edgemap (parallel) time: %.3f seconds (%.1f%%)\n", 
            edgemapTime, (edgemapTime/totalTime)*100);
        System.out.printf("Non-edgemap (sequential) time: %.3f seconds (%.1f%%)\n", 
            nonEdgemapTime, (nonEdgemapTime/totalTime)*100);

        // Calculate theoretical speedup using Amdahl's Law
        double parallelFraction = edgemapTime / totalTime;
        double speedup4 = 1.0 / ((1.0 - parallelFraction) + (parallelFraction / 4.0));
        double speedup1000 = 1.0 / ((1.0 - parallelFraction) + (parallelFraction / 1000.0));

        System.out.println("\nAMDAHL'S LAW PREDICTIONS:");
        System.out.printf("Parallel portion of code: %.1f%%\n", parallelFraction * 100);
        System.out.printf("Predicted speedup with 4 threads: %.2fx\n", speedup4);
        System.out.printf("Predicted speedup with 1000 threads: %.2fx\n", speedup1000);

        if(delta > tol) {
            System.err.println("Error: solution has not converged.");
        }

        return x;
    }

    public static void validate(SparseMatrix matrix, double[] x) {
        long tm_start = System.nanoTime();
        final int n = matrix.getNumVertices();
        double v[] = new double[n];
        double y[] = new double[n];
        final double d = 0.85;
        final double tol = 1e-7;

        for(int i=0; i < n; ++i) {
            v[i] = 1.0 / (double)n;
            y[i] = 0;
        }

        int outdeg[] = new int[n];
        matrix.calculateOutDegree(outdeg);

        double tm_init = (double)(System.nanoTime() - tm_start) * 1e-9;
        System.err.println("Initialisation: " + tm_init + " seconds");
        tm_start = System.nanoTime();

        PageRankRelax PRrelax = new PageRankRelax(outdeg, d, x, y);
        ParallelContext context = ParallelContextHolder.get();

        context.edgemap(matrix, PRrelax);

        double w = 1.0 - sum(y, n);
        for(int i=0; i < n; ++i)
            y[i] += w * v[i];

        double delta = normdiff(x, y, n);
        System.err.println("delta: " + delta);

        if(Double.isNaN(delta)) {
            System.err.println("Error: NaN value encountered: delta=" + delta);
            System.exit(43);
        } else if(delta > tol) {
            System.err.println("Error: tolerance not met: delta=" + delta);
            System.exit(43);
        } else {
            System.err.println("Success.");
            System.exit(42);
        }
    }

    static private double sum(double[] a, int n) {
        double d = 0.;
        double err = 0.;
        for(int i=0; i < n; ++i) {
            double tmp = d;
            double y = a[i] + err;
            d = tmp + y;
            err = tmp - d;
            err += y;
        }
        return d;
    }

    static private double normdiff(double[] a, double[] b, int n) {
        double d = 0.;
        double err = 0.;
        for(int i=0; i < n; ++i) {
            double tmp = d;
            double y = Math.abs(b[i] - a[i]) + err;
            d = tmp + y;
            err = tmp - d;
            err += y;
        }
        return d;
    }
}