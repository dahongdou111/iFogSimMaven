package org.fog.test.perfeval.ReinfocementLearning;

import ai.djl.Model;
import ai.djl.metric.Metrics;
import ai.djl.ndarray.NDArray;
import ai.djl.ndarray.NDManager;
import ai.djl.ndarray.types.DataType;
import ai.djl.ndarray.types.Shape;
import ai.djl.nn.SequentialBlock;
import ai.djl.nn.core.Linear;
import ai.djl.training.DefaultTrainingConfig;
import ai.djl.training.EasyTrain;
import ai.djl.training.Trainer;
import ai.djl.training.dataset.ArrayDataset;
import ai.djl.training.dataset.Batch;
import ai.djl.training.listener.TrainingListener;
import ai.djl.training.loss.Loss;
import ai.djl.training.optimizer.Optimizer;
import ai.djl.training.tracker.Tracker;
import ai.djl.translate.TranslateException;

import java.io.IOException;

public class ConciseLinearRegression {
    static class DataPoints {
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

        static public DataPoints syntheticData(NDManager manager, NDArray w, float b, int numExample) {
            NDArray X = manager.randomNormal(new Shape(numExample, w.size()));
            NDArray y = X.dot(w).add(b); // 向量点成w + b
            // 添加噪声
            y = y.add(manager.randomNormal(0, 0.01f, y.getShape(), DataType.FLOAT32));
            return new DataPoints(X, y);
        }
    }

    // Saved in the utils file for later use
    static public ArrayDataset loadArray(NDArray features, NDArray labels, int batchSize, boolean shuffle) {
        return new ArrayDataset.Builder()
                .setData(features) // set the features
                .optLabels(labels) // set the labels
                .setSampling(batchSize, shuffle) // set the batch size and random sampling
                .build();
    }

    public static void main(String[] args) throws IOException, TranslateException {
        NDManager manager = NDManager.newBaseManager();

        NDArray trueW = manager.create(new float[]{2, -3.4f});
        float trueB = 4.2f;

        DataPoints dp = DataPoints.syntheticData(manager, trueW, trueB, 1000);
        NDArray features = dp.getX();
        NDArray labels = dp.getY();

        int batchSize = 10;
        ArrayDataset dataset = loadArray(features, labels, batchSize, false);

//        Batch batch = dataset.getData(manager).iterator().next();
//        NDArray X = batch.getData().head();
//        NDArray y = batch.getLabels().head();
//        System.out.println(X);
//        System.out.println(y);
//        batch.close();

        Model model = Model.newInstance("lin-reg");
        SequentialBlock net = new SequentialBlock();
        Linear linearBlock = Linear.builder().optBias(true).setUnits(1).build();
        net.add(linearBlock);

        model.setBlock(net);

        Loss l2loss = Loss.l2Loss();

        Tracker lrt = Tracker.fixed(0.03f);  // 固定学习率
        Optimizer sgd = Optimizer.sgd().setLearningRateTracker(lrt).build(); // 优化器

        DefaultTrainingConfig config = new DefaultTrainingConfig(l2loss)
                .optOptimizer(sgd)  //  Optimizer ( loss function )
                .optDevices(manager.getEngine().getDevices(1))
                .addTrainingListeners(TrainingListener.Defaults.logging()); // Logging

        Trainer trainer = model.newTrainer(config);

        // First axis is batch size - won't impact parameter initialization
        // Second axis is the input size
        trainer.initialize(new Shape(batchSize, 2));

        Metrics metrics = new Metrics();
        trainer.setMetrics(metrics);

        int numEpochs = 3;

        for (int epoch = 1; epoch <= numEpochs; epoch++) {
            System.out.printf("Epoch %d\n", epoch);
            // Iterate over dataset
            for (Batch batch : trainer.iterateDataset(dataset)) {
                // Update loss and evaulator
                EasyTrain.trainBatch(trainer, batch);

                // Update parameters
                trainer.step();

                batch.close();
            }
            // reset training and validation evaluators at end of epoch
            trainer.notifyListeners(listener -> listener.onEpoch(trainer));
        }
    }
}
