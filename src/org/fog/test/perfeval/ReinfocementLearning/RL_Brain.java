package org.fog.test.perfeval.ReinfocementLearning;

import ai.djl.Model;
import ai.djl.inference.Predictor;
import ai.djl.ndarray.NDArray;
import ai.djl.ndarray.NDList;
import ai.djl.ndarray.NDManager;
import ai.djl.ndarray.types.Shape;
import ai.djl.nn.Activation;
import ai.djl.nn.Block;
import ai.djl.nn.Blocks;
import ai.djl.nn.SequentialBlock;
import ai.djl.nn.core.Linear;
import ai.djl.training.*;
import ai.djl.training.evaluator.Accuracy;
import ai.djl.training.initializer.NormalInitializer;
import ai.djl.training.listener.TrainingListener;
import ai.djl.training.loss.Loss;
import ai.djl.training.optimizer.Adam;
import ai.djl.training.optimizer.Optimizer;
import ai.djl.training.tracker.Tracker;
import ai.djl.util.Pair;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;


//SequentialBlock block = new SequentialBlock();
//
//block.add(Blocks.batchFlattenBlock());
//block.add(Linear.builder().setUnits(128).build());
//block.add(Activation::relu);
//block.add(Linear.builder().setUnits(64).build());
//block.add(Activation::relu);
//block.add(Linear.builder().setUnits(10).build());

public class RL_Brain {
    // 超参数
    static float GAMMA = 0.9f;
    static float LR_A = 0.001f;
    static float LR_C = 0.01f;
    static int MAX_EP_STEPS = 100;


    static class Actor {
        Linear acts_prob;
        DefaultTrainingConfig config;
        Model model;
        public Actor(int n_features, int n_actions, float lr){

            model = Model.newInstance("Actor");

            SequentialBlock net = new SequentialBlock();
            net.add(Blocks.batchFlattenBlock(n_features+1));
            net.add(Linear.builder().optBias(true).setUnits(20).build());
            net.add(Activation::relu);
            net.add(Linear.builder().optBias(true).setUnits(n_actions).build());
            model.setBlock(net);


            config = new DefaultTrainingConfig(Loss.l2Loss())
                    .optOptimizer(Adam.builder().optLearningRateTracker(Tracker.fixed(lr)).build())
                    .addEvaluator(new Accuracy())
                    .optInitializer(new NormalInitializer(), "normal")
                    .addTrainingListeners(TrainingListener.Defaults.basic());
        }

        public NDArray learn(NDArray s, NDArray a, NDArray td_error){
            try (Trainer trainer = model.newTrainer(config)) {
                try (GradientCollector collector = trainer.newGradientCollector()) {
                    NDArray log_prob = trainer.forward(new NDList(s)).singletonOrThrow().get(0).log();
                    NDArray exp_v = log_prob.mul(td_error).mean(); // log 概率 * TD 方向


                    collector.backward(exp_v.neg());
                    trainer.step();

                    return exp_v;
                }
            }
        }

        public NDArray choose_action(NDArray s) {
            NDArray mm = s.getManager().ones(new Shape(4));
            NDList ss = new NDList(mm);
            try (Trainer trainer = model.newTrainer(config)) {
                // First axis is batch size - won't impact parameter initialization
                trainer.initialize(new Shape(1, 2));
//                System.out.println(trainer.evaluate(ss));
                NDArray probs = trainer.evaluate(ss).singletonOrThrow();
                System.out.println(probs);
                return probs;
            }
        }
    }

    static class Critic{
        Model model;
        DefaultTrainingConfig config;
        public Critic(int n_features, float lr){
            model = Model.newInstance("Critic");

            SequentialBlock net = new SequentialBlock();
            net.add(Blocks.batchFlattenBlock(n_features));
            net.add(Linear.builder().optBias(true).setUnits(20).build());
            net.add(Activation::relu);
            net.add(Linear.builder().optBias(true).setUnits(1).build());
            model.setBlock(net);

            config = new DefaultTrainingConfig(Loss.l2Loss())
                    .optOptimizer(Adam.builder().optLearningRateTracker(Tracker.fixed(lr)).build())
                    .addEvaluator(new Accuracy())
                    .optInitializer(new NormalInitializer(), "normal")
                    .addTrainingListeners(TrainingListener.Defaults.basic());

        }

        public NDArray learn(NDArray s, NDArray r, NDArray s_) {
            try (Trainer trainer = model.newTrainer(config)) {
                trainer.initialize(new Shape(1, 1));
                try (GradientCollector collector = trainer.newGradientCollector()) {
                    NDArray v_ = trainer.forward(new NDList(s_)).singletonOrThrow();
                    NDArray v = trainer.forward(new NDList(s)).singletonOrThrow();
                    NDArray td_error = v_.mul(GAMMA).add(r).sub(v);
                    NDArray loss = td_error.square();

                    collector.backward(loss);
                    trainer.step();
                    return td_error;
                }
            }
        }

//        public static Pair<Float, Float> get_env_feedback(float s, float a) {
//
//        }


    }

    public static void main(String[] args){
        Actor actor = new Actor(1, 1, LR_A);
        Critic critic = new Critic(1, LR_C);

        float end = 5.0f; // 终点

        for(int i=0; i<10000; i++){
            float s = 1.0f; // 环境状态
            int t = 0;
            float track_r = 0.0f;

            NDManager manager = NDManager.newBaseManager();

            while(true){
                NDArray a = actor.choose_action(manager.create(s));

                float s_;
                float r = a.getFloat(0);
                if(a.getFloat(0) < 0) {
                    s_ = s - 0.1f;
                    r = -1.0f;
                } else {
                    s_ = s + 0.1f;
                    r = 1.0f;
                }

                boolean done = false;
                if (s_ > end) {
                    r = 2.0f;
                    done = true;
                } else if(s_ < 0) {
                    done = true;
                }

                track_r += r;

                NDArray td_error = critic.learn(manager.create(s), manager.create(r), manager.create(s_));
                actor.learn(manager.create(s), a, td_error);

                s = s_;
                t += 1;

//                    System.out.println("index:" + s_);

                if (done || (t>=MAX_EP_STEPS)) {
                    // 回合结束，打印回合累积奖励
                    float ep_rs_sum = track_r;
                    System.out.println("episode:" + i + "  reward:" + ep_rs_sum);
                    break;
                }
            }
        }
    }
}
