package uk.ac.qub.csc3021.graph;

public class ParallelContextSimple extends ParallelContext {
    private class ThreadSimple extends Thread {
        private SparseMatrix matrix;
        private Relax relax;
        private int fromVertex;
        private int toVertex;

        public ThreadSimple(SparseMatrix matrix, Relax relax, int from, int to) {
            this.matrix = matrix;
            this.relax = relax;
            this.fromVertex = from;
            this.toVertex = to;
        }

        public void run() {
            matrix.ranged_edgemap(relax, fromVertex, toVertex);
        }
    }
    
    public ParallelContextSimple(int num_threads_) {
        super(num_threads_);
    }

    public void terminate() { }

    public void edgemap(SparseMatrix matrix, Relax relax) {
        int numThreads = getNumThreads();
        int numVertices = matrix.getNumVertices();
        
        // Calculate vertices per thread
        int verticesPerThread = numVertices / numThreads;
        int extraVertices = numVertices % numThreads;

        ThreadSimple[] threads = new ThreadSimple[numThreads];

        // Create and start threads
        int start = 0;
        for(int i = 0; i < numThreads; i++) {
            int verticesForThisThread = verticesPerThread + (i < extraVertices ? 1 : 0);
            int end = start + verticesForThisThread;
            
            threads[i] = new ThreadSimple(matrix, relax, start, end);
            threads[i].start();
            
            start = end;
        }

        // Wait for all threads
        try {
            for(int i = 0; i < numThreads; i++) {
                threads[i].join();
            }
        } catch(InterruptedException e) {
            System.err.println("Thread interrupted: " + e);
        }
    }
}