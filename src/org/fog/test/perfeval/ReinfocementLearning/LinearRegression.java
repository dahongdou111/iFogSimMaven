package org.fog.test.perfeval.ReinfocementLearning;

import ai.djl.engine.Engine;
import ai.djl.ndarray.NDArray;
import ai.djl.ndarray.NDList;
import ai.djl.ndarray.NDManager;
import ai.djl.ndarray.index.NDIndex;
import ai.djl.ndarray.types.DataType;
import ai.djl.ndarray.types.Shape;
import ai.djl.training.GradientCollector;
import ai.djl.training.dataset.ArrayDataset;
import ai.djl.training.dataset.Batch;
import ai.djl.translate.TranslateException;
import com.google.common.collect.Table;

import java.io.IOException;

public class LinearRegression {
    class DataPoints {
        private NDArray X, y;
        public DataPoints(NDArray X, NDArray y) {
            this.X = X;
            this.y = y;
        }

        public NDArray getX() {
            return X;
        }

        public NDArray getY() {
            return y;
        }
    }

    public DataPoints syntheticData(NDManager manager, NDArray w, float b, int numExample) {
        NDArray X = manager.randomNormal(new Shape(numExample, w.size()));
        NDArray y = X.dot(w).add(b); // 向量点成w + b
        // 添加噪声
        y = y.add(manager.randomNormal(0, 0.01f, y.getShape(), DataType.FLOAT32));
        return new DataPoints(X, y);
    }

    // Saved in Training.java for later use
    public NDArray linreg(NDArray X, NDArray w, NDArray b) {
        return X.dot(w).add(b);
    }

    // Saved in Training.java for later use
    public NDArray squaredLoss(NDArray yHat, NDArray y) {
        return (yHat.sub(y.reshape(yHat.getShape()))).mul
                ((yHat.sub(y.reshape(yHat.getShape())))).div(2);
    }

    // Saved in Training.java for later use
    public static void sgd(NDList params, float lr, int batchSize) {
        for (int i = 0; i < params.size(); i++) {
            NDArray param = params.get(i);
            // Update param
            // param = param - param.gradient * lr / batchSize
            param.subi(param.getGradient().mul(lr).div(batchSize));
        }
    }

    public static void main(String[] args) throws IOException, TranslateException {
        LinearRegression lrg = new LinearRegression();

        NDManager manager = NDManager.newBaseManager();

        NDArray trueW = manager.create(new float[]{2, -3.4f});
        float trueB = 4.2f;

        DataPoints dp = lrg.syntheticData(manager, trueW, trueB, 1000);
        NDArray features = dp.getX();
        NDArray labels = dp.getY();

        System.out.printf("features: [%f, %f]\n", features.get(0).getFloat(0), features.get(0).getFloat(1));
        System.out.println("label: " + labels.getFloat(0));

        float[] X = features.get(new NDIndex(":, 1")).toFloatArray();
        float[] y = labels.toFloatArray();

        // 批处理数据
        int batchSize = 10;

        ArrayDataset dataset = new ArrayDataset.Builder()
                                    .setData(features)
                                    .optLabels(labels)
                                    .setSampling(batchSize, false)
                                    .build();
//
//        Batch batch = dataset.getData(manager).iterator().next();
//        // Call head() to get the first NDArray
//        NDArray X1 = batch.getData().head();
//        NDArray y1 = batch.getLabels().head();
//        System.out.println(X1);
//        System.out.println(y1);
//
//        batch.close();

        NDArray w = manager.randomNormal(0, 0.01f, new Shape(2, 1), DataType.FLOAT32);
        NDArray b = manager.zeros(new Shape(1));
        NDList params = new NDList(w, b);

        float lr = 0.03f;
        int numEpochs = 3;

        // 允许梯度
        for (NDArray param : params) {
            param.setRequiresGradient(true);
        }

        for (int epoch = 0; epoch < numEpochs; epoch++) {
            // Assuming the number of examples can be divided by the batch size, all
            // the examples in the training dataset are used once in one epoch
            // iteration. The features and tags of minibatch examples are given by X
            // and y respectively.
            for (Batch batch : dataset.getData(manager)) {
                NDArray X1 = batch.getData().head();
                NDArray y1 = batch.getLabels().head();

                try (GradientCollector gc = Engine.getInstance().newGradientCollector()) {
                    // Minibatch loss in X and y
                    NDArray l = lrg.squaredLoss(lrg.linreg(X1, params.get(0), params.get(1)), y1);
                    gc.backward(l);  // Compute gradient on l with respect to w and b
                }
                sgd(params, lr, batchSize);  // Update parameters using their gradient

                batch.close();
            }
            NDArray trainL = lrg.squaredLoss(lrg.linreg(features, params.get(0), params.get(1)), labels);
            System.out.printf("epoch %d, loss %f\n", epoch + 1, trainL.mean().getFloat());
        }
    }

}
