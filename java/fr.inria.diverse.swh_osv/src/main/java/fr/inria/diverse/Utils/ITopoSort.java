package fr.inria.diverse.Utils;

import java.io.IOException;

import org.softwareheritage.graph.SwhBidirectionalGraph;

import it.unimi.dsi.fastutil.longs.LongBigArrayBigList;

public interface ITopoSort {

    public LongBigArrayBigList getTopoOrder(SwhBidirectionalGraph graph, String path) throws IOException;

}
