package org.fog.test.perfeval.ReinfocementLearning;

import ai.djl.ndarray.NDArray;
import ai.djl.ndarray.NDManager;
import ai.djl.ndarray.index.NDIndex;
import ai.djl.ndarray.types.Shape;
import ai.djl.nn.Activation;
import ai.djl.nn.Blocks;
import ai.djl.nn.SequentialBlock;
import ai.djl.nn.core.Linear;
import com.google.common.base.Stopwatch;


public class test {
    public static void main(String[] args){
        SequentialBlock block = new SequentialBlock();

        block.add(Blocks.batchFlattenBlock());
        block.add(Linear.builder().setUnits(128).build());
        block.add(Activation::relu);
        block.add(Linear.builder().setUnits(64).build());
        block.add(Activation::relu);
        block.add(Linear.builder().setUnits(10).build());

        System.out.println(block);

        int n = 10000;
        NDManager manager = NDManager.newBaseManager();
        NDArray a = manager.ones(new Shape(n));
        NDArray b = manager.ones(new Shape(n));

        NDArray c = a.add(b);

        System.out.println(c);
    }
}
