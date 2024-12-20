package uk.ac.qub.csc3021.graph;

import java.io.*;

public class SparseMatrixCSC extends SparseMatrix {
    // Instance variables
    private int[] rowIndices;  // Source vertices
    private int[] colPointers; // Start of each column
    private int num_vertices;
    private int num_edges;

    public SparseMatrixCSC(String file) {
        try {
            InputStreamReader is = new InputStreamReader(new FileInputStream(file), "UTF-8");
            BufferedReader rd = new BufferedReader(is);
            readFile(rd);
        } catch(FileNotFoundException e) {
            System.err.println("File not found: " + e);
            return;
        } catch(UnsupportedEncodingException e) {
            System.err.println("Unsupported encoding exception: " + e);
            return;
        } catch(Exception e) {
            System.err.println("Exception: " + e);
            return;
        }
    }

    int getNext(BufferedReader rd) throws Exception {
        String line = rd.readLine();
        if(line == null)
            throw new Exception("premature end of file");
        return Integer.parseInt(line);
    }

    void readFile(BufferedReader rd) throws Exception {
        String line = rd.readLine();
        if(line == null)
            throw new Exception("premature end of file");
        if(!line.equalsIgnoreCase("CSC") && !line.equalsIgnoreCase("CSC-CSR"))
            throw new Exception("file format error -- header");
        
        num_vertices = getNext(rd);
        num_edges = getNext(rd);

        // Initialize arrays
        rowIndices = new int[num_edges];        // For source vertices
        colPointers = new int[num_vertices + 1]; // +1 for last vertex endpoint
        int currentEdge = 0;

        // Read the edges
        for(int i = 0; i < num_vertices; ++i) {
            line = rd.readLine();
            if(line == null)
                throw new Exception("premature end of file");
            
            String[] elm = line.split(" ");
            assert Integer.parseInt(elm[0]) == i : "Error in CSC file";
            
            colPointers[i] = currentEdge;
            
            // Store source vertices
            for(int j = 1; j < elm.length; ++j) {
                rowIndices[currentEdge++] = Integer.parseInt(elm[j]);
            }
        }
        colPointers[num_vertices] = num_edges; // Set final pointer
    }

    public void edgemap(Relax relax) {
        System.err.println("DEBUG: Starting edgemap processing " + num_edges + " edges");
        long startTime = System.nanoTime();
        
        for(int dst = 0; dst < num_vertices; dst++) {
            for(int edge = colPointers[dst]; edge < colPointers[dst + 1]; edge++) {
                relax.relax(rowIndices[edge], dst);
            }
        }
        
        double processingTime = (System.nanoTime() - startTime) / 1e9;
        System.err.println("DEBUG: Finished edgemap in " + processingTime + " seconds");
    }

    public void ranged_edgemap(Relax relax, int from, int to) {
		// Process vertices from 'from' to 'to'
		for(int dst = from; dst < to; dst++) {
			// For each vertex in our range
			for(int edge = colPointers[dst]; edge < colPointers[dst + 1]; edge++) {
				// Process each incoming edge
				int src = rowIndices[edge];
				relax.relax(src, dst);
			}
		}
	}

    public void calculateOutDegree(int outdeg[]) {
        // Initialize array
        for(int i = 0; i < num_vertices; i++) {
            outdeg[i] = 0;
        }
        
        // Count outgoing edges
        for(int dst = 0; dst < num_vertices; dst++) {
            for(int edge = colPointers[dst]; edge < colPointers[dst + 1]; edge++) {
                int src = rowIndices[edge];
                outdeg[src]++;
            }
        }
    }

    public int getNumVertices() { return num_vertices; }
    public int getNumEdges() { return num_edges; }
}