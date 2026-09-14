package fr.inria.diverse.Utils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.softwareheritage.graph.AllowedNodes;
import org.softwareheritage.graph.Subgraph;
import org.softwareheritage.graph.SwhBidirectionalGraph;

import it.unimi.dsi.big.webgraph.LazyLongIterator;
import it.unimi.dsi.big.webgraph.NodeIterator;
import it.unimi.dsi.fastutil.longs.LongBigArrayBigList;
import me.tongfei.progressbar.ProgressBar;

/* Lists all nodes nodes of the types given as argument, in topological order,
 * from leaves (contents, if selected) to the top (origins, if selected).
 *
 * This uses a DFS, so nodes are likely to be close to their neighbors.
 *
 * Some extra information is provided to allow more efficient consumption
 * of the output: number of ancestors, successors, and a sample of two ancestors.
 *
 * Sample invocation:
 *
 *   $ java -cp ~/swh-environment/swh-graph/java/target/swh-graph-*.jar -Xmx1000G -XX:PretenureSizeThreshold=512M -XX:MaxNewSize=4G -XX:+UseLargePages -XX:+UseTransparentHugePages -XX:+UseNUMA -XX:+UseTLAB -XX:+ResizeTLAB org.softwareheritage.graph.utils.TopoSort /dev/shm/swh-graph/default/graph dfs backward 'rev,rel,snp,ori' \
 *      | pv --line-mode --wait \
 *      | zstdmt \
 *      > /poolswh/softwareheritage/vlorentz/2022-04-25_toposort_rev,rel,snp,ori.txt.zst
 */

public class TopoSort implements ITopoSort, java.io.Serializable {

    private boolean dfs; // Whether to run in BFS or DFS
    private LongBigArrayBigList ready;
    private long endIndex; // In BFS mode, index of the end of the queue where to pop from; in DFS mode,
                           // index of the
                           // top of the stack

    private static final Logger logger = LogManager.getLogger(TopoSort.class);

    @Override
    public MyLongBigArrayBigList getTopoOrder(SwhBidirectionalGraph fullgraph, String path) throws IOException {

        // checking if the topoorder has already been computed
        File f = new File(path);
        if (f.exists() && !f.isDirectory()) {
            FileInputStream fis = new FileInputStream(path);
            ObjectInputStream ois = new ObjectInputStream(fis);
            try {
                return (MyLongBigArrayBigList) ois.readObject();
            } catch (ClassNotFoundException e) {
                e.printStackTrace();
            }
        }

        // loading the graph and the settings
        SwhBidirectionalGraph transposedFullGraph = fullgraph.transpose();
        String nodeType = "rev";
        Subgraph transposedSubGraph = new Subgraph(fullgraph, new AllowedNodes(nodeType)).transpose();
        MyLongBigArrayBigList topoOrder = new MyLongBigArrayBigList(transposedSubGraph.numNodes() + 1);
        dfs = true;
        ready = new LongBigArrayBigList(transposedSubGraph.numNodes());

        // toposort
        MyLongBigArrayBigList unvisitedAncestors = new MyLongBigArrayBigList(fullgraph.numNodes());
        long total_edges = 0;

        /* First, push all roots to the stack */
        try (ProgressBar pb = new ProgressBar("Topo Sort - Listing roots...", transposedSubGraph.numNodes() + 1)) {

            NodeIterator nodeIterator = transposedSubGraph.nodeIterator();
            while (nodeIterator.hasNext()) {
                pb.step();
                long currentNodeId = nodeIterator.nextLong();
                long nbPredecessor = transposedSubGraph.indegree(currentNodeId);
                unvisitedAncestors.set(currentNodeId, nbPredecessor);
                total_edges += nbPredecessor;
                if (nbPredecessor != 0) {
                    /* The node has predecessor, so it is not a root. */
                    continue;
                }
                pushReady(currentNodeId);
            }
        }
        /* sorting the nodes */
        System.err.println("Root listed, starting traversal.");
        try (ProgressBar pb = new ProgressBar("Topo Sort - sorting...", total_edges)) {

            int i = 0;

            while (readyNodes()) {
                long currentNodeId = popReady();

                /* Find its successors which are ready */
                LazyLongIterator successors = transposedFullGraph.successors(currentNodeId);
                for (long successorNodeId; (successorNodeId = successors.nextLong()) != -1;) {
                    if (!transposedSubGraph.nodeExists(successorNodeId)) {
                        /* Arc destination is filtered out from the subgraph, ignore it */
                        continue;
                    }
                    pb.step();
                    long nbUnvisitedAncestors = unvisitedAncestors.getLong(successorNodeId);
                    nbUnvisitedAncestors--;
                    if (nbUnvisitedAncestors == 0) {
                        pushReady(successorNodeId);
                    } else if (nbUnvisitedAncestors < 0) {
                        System.err.format("[BUG] %s has negative number of unvisited ancestors: %d\n",
                                transposedSubGraph.getSWHID(successorNodeId), nbUnvisitedAncestors);
                    }
                    unvisitedAncestors.set(successorNodeId, nbUnvisitedAncestors);
                }

                topoOrder.set(i, currentNodeId);
                i++;
            }
            export(path, topoOrder);

            return topoOrder;
        }
    }

    public void export(String path, MyLongBigArrayBigList topoOrder) throws IOException {
        FileOutputStream fileOutputStream = new FileOutputStream(path);
        ObjectOutputStream objectOutputStream = new ObjectOutputStream(fileOutputStream);
        objectOutputStream.writeObject(topoOrder);
        objectOutputStream.flush();
        objectOutputStream.close();
    }

    private long readySize() {
        if (dfs) {
            return endIndex;
        } else {
            return ready.size64() - endIndex;
        }
    }

    private boolean readyNodes() {
        return readySize() > 0;
    }

    private void pushReady(long nodeId) {
        if (dfs) {
            endIndex++;
        }
        ready.add(nodeId);
    }

    private long popReady() {
        if (dfs) {
            return ready.removeLong(--endIndex);
        } else {
            return ready.getLong(endIndex++);
        }
    }

    public class MyLongBigArrayBigList extends LongBigArrayBigList {
        public MyLongBigArrayBigList(long size) {
            super(size);

            // Allow setting directly in the array without repeatedly calling
            // .add() first
            this.size = size;
        }
    }

    /**
     * for testing purposes
     * 
     * @param args
     * @throws IOException
     */
   
}
