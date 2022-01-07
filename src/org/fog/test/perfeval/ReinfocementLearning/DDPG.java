package org.fog.test.perfeval.ReinfocementLearning;

import ai.djl.Device;
import ai.djl.Model;
import ai.djl.ndarray.NDArray;
import ai.djl.ndarray.NDList;
import ai.djl.ndarray.NDManager;
import ai.djl.ndarray.index.NDIndex;
import ai.djl.ndarray.types.DataType;
import ai.djl.ndarray.types.Shape;
import ai.djl.nn.*;
import ai.djl.nn.core.Linear;
import ai.djl.training.DefaultTrainingConfig;
import ai.djl.training.GradientCollector;
import ai.djl.training.ParameterStore;
import ai.djl.training.Trainer;
import ai.djl.training.dataset.Dataset;
import ai.djl.training.evaluator.Accuracy;
import ai.djl.training.initializer.NormalInitializer;
import ai.djl.training.listener.TrainingListener;
import ai.djl.training.loss.Loss;
import ai.djl.training.optimizer.Adam;
import ai.djl.training.tracker.Tracker;
import ai.djl.util.Pair;
import ai.djl.util.PairList;
import ai.djl.util.Preconditions;
import ai.djl.util.RandomUtils;

public class DDPG {
    // 超参数
    static float LR_A = 0.001f;
    static float LR_C = 0.001f;
    static float GAMMA = 0.9f;  // reward discount
    static float TAU = 0.01f;   // soft replacement
    static int MEMORY_CAPACITY = 10000;
    static int BATCH_SIZE = 32;

    //
    NDArray memory;             // 经验池，MEMORY_CAPACITY * (s_dim * 2 + a_dim + 1)即(s, a, r, s_)
    int pointer;                // 经验池替换指针
    boolean memory_full;        // 经验池是否满了

    // 环境信息
    int a_dim, s_dim;
    float a_bound;

    // models
    Model actor_eval, actor_target, critic_eval, critic_target;
    ParameterList ae_params, at_params, ce_params, ct_params;
    DefaultTrainingConfig a_config, c_config;
    Trainer ae_trainer, at_trainer, ce_trainer, ct_trainer;

    int a_replace_counter, c_replace_counter;

    //
    NDManager manager;
    public DDPG(int a_dim, int s_dim, Pair<Float, Float> a_bound) {
        this.manager = NDManager.newBaseManager();
        this.memory = manager.zeros(new Shape(MEMORY_CAPACITY, s_dim * 2 + a_dim + 1), DataType.FLOAT32);
        this.pointer = 0;
        this.memory_full = false;
        this.a_replace_counter = 0;
        this.c_replace_counter = 0;
        // 环境信息初始化
        this.a_dim = a_dim; this.s_dim = s_dim; this.a_bound = a_bound.getValue();
        // 模型初始化
        this.actor_eval = _build_a("actor_eval");
        this.actor_target = _build_a("actor_target");
        this.critic_eval = _build_c("critic_eval");
        this.critic_target = _build_c("critic_target");
        // 网络参数
        this.ae_params = actor_eval.getBlock().getParameters();
        this.at_params = actor_target.getBlock().getParameters();
        this.ce_params = critic_eval.getBlock().getParameters();
        this.ct_params = critic_target.getBlock().getParameters();
        //
        this.a_config = new DefaultTrainingConfig(Loss.l2Loss())
                .optOptimizer(Adam.builder().optLearningRateTracker(Tracker.fixed(LR_A)).build())
                .addEvaluator(new Accuracy())
                .optInitializer(new NormalInitializer(), "normal")
                .addTrainingListeners(TrainingListener.Defaults.basic());
        this.c_config = new DefaultTrainingConfig(Loss.l2Loss())
                .optOptimizer(Adam.builder().optLearningRateTracker(Tracker.fixed(LR_C)).build())
                .addEvaluator(new Accuracy())
                .optInitializer(new NormalInitializer(), "normal")
                .addTrainingListeners(TrainingListener.Defaults.basic());
        //
        ae_trainer = this.actor_eval.newTrainer(a_config);
        at_trainer = this.actor_target.newTrainer(a_config);
        ce_trainer = this.critic_eval.newTrainer(c_config);
        ct_trainer = this.critic_target.newTrainer(c_config);
        ae_trainer.initialize(new Shape(1, this.s_dim));
        at_trainer.initialize(new Shape(1, this.s_dim));
        ce_trainer.initialize(new Shape(1, this.s_dim+this.a_dim));
        ct_trainer.initialize(new Shape(1, this.s_dim+this.a_dim));
    }

    public NDArray choose_action(NDArray s) {
        try {
            NDArray a = ae_trainer.evaluate(new NDList(s)).singletonOrThrow();
            a = a.mul(this.a_bound);
            return a;
        } catch (Exception e){
            e.printStackTrace();
        }
        return null;
    }

    public void learn() {
        // soft target replacement. 目标网络参数替换
//        System.out.println("tttttttttt");
        for(int i=0; i<at_params.size(); i++){
            Parameter p = at_params.get(i).getValue();
            NDArray tt = p.getArray().mul(1.0-TAU).add(ae_params.get(i).getValue().getArray().mul(TAU));
//            p.close(); p.setShape(null);
            tt.copyTo(p.getArray());
//            System.out.println(p.getArray());
        }
        for(int i=0; i<ct_params.size(); i++){
            Parameter p = ct_params.get(i).getValue();
            NDArray tt = p.getArray().mul(1.0-TAU).add(ce_params.get(i).getValue().getArray().mul(TAU));
//            p.close();
//            p.setArray(tt.mul(1.0-TAU).add(ce_params.get(i).getValue().getArray().mul(TAU)));
            tt.copyTo(p.getArray());
        }

        // 经验采样学习
        NDManager tmp_manager = NDManager.newBaseManager();
        NDArray indices = tmp_manager.randomInteger(0, MEMORY_CAPACITY, new Shape(BATCH_SIZE), DataType.INT32);
        NDArray label = tmp_manager.zeros(new Shape(MEMORY_CAPACITY), DataType.BOOLEAN);
        for(int i=0; i<indices.size(); i++) label.set(new NDIndex(indices.getInt(i)), 1);
        NDArray bt = this.memory.get(label);
        NDArray bs = bt.get(":, :{}", this.s_dim);
        NDArray ba = bt.get(":, {}:{}", this.s_dim, this.s_dim + this.a_dim);
        NDArray br = bt.get(":, {}:{}", -this.s_dim-1, -this.s_dim);
        NDArray bs_ = bt.get(":, {}:", -this.s_dim);

        try (GradientCollector ae_collector = ae_trainer.newGradientCollector()) {
            // Actor 反向传播
            NDArray aa = ae_trainer.forward(new NDList(bs)).singletonOrThrow();
            NDArray qq = ce_trainer.evaluate(new NDList(bs.concat(aa, 1))).singletonOrThrow();//c_eval网络对a_eval动作评估 Q(s, a)
            NDArray a_loss = qq.mean().neg(); // 使Q值最大的损失
            ae_collector.backward(a_loss);
            ae_trainer.step();
        }
        try (GradientCollector ce_collector = ce_trainer.newGradientCollector()){
            // Critic 反向传播
            NDArray q = ce_trainer.forward(new NDList(bs.concat(ba, 1))).singletonOrThrow();
            NDArray a_ = at_trainer.evaluate(new NDList(bs_)).singletonOrThrow();
            NDArray q_ = ct_trainer.evaluate(new NDList(bs_.concat(a_, 1))).singletonOrThrow();

            NDArray q_target = q_.mul(GAMMA).add(br);

            NDArray td_error = q_target.sub(q).square();        // (q_target - q) ** 2
            NDArray td_loss = td_error.mean().div(td_error.size()); // loss = mean / size
            ce_collector.backward(td_loss);
            ce_trainer.step();
        }
    }

    public void store_transition(NDArray s, NDArray a, NDArray r, NDArray s_) {
        NDArray transition = s.concat(a, 1).concat(r, 1).concat(s_, 1);
        int index = this.pointer % MEMORY_CAPACITY;
        this.memory.set(new NDIndex(index), transition);
        this.pointer++;
        if (this.pointer > MEMORY_CAPACITY){
            this.memory_full = true;
        }
    }

    Model _build_a(String name) {
        Model model = Model.newInstance(name);
        SequentialBlock net = new SequentialBlock();
        net.add(Blocks.batchFlattenBlock(this.s_dim));
        net.add(Linear.builder().optBias(true).setUnits(100).build());
        net.add(Activation::relu);
        net.add(Linear.builder().optBias(true).setUnits(this.a_dim).build());
        net.add(Activation::tanh);
        model.setBlock(net);

        return model;
    }

    Model _build_c(String name) {
        Model model = Model.newInstance(name);
        SequentialBlock net = new SequentialBlock();
        net.add(Blocks.batchFlattenBlock(this.s_dim + this.a_dim));
        net.add(new MyBlock(s_dim, a_dim));
        net.add(Linear.builder().setUnits(1).optBias(true).build());
        model.setBlock(net);

        return model;
    }

    public NDManager getManager() {
        return manager;
    }

    // 控制
    static int MAX_EPISODES = 500;
    static int MAX_EP_STEPS = 200;
    static boolean ON_TRAIN = true;
    static void train(DDPG ddpg) {
        NDManager manager = ddpg.getManager();
        NDArray goal = manager.create(new float[]{5.0f, 5.0f});
        for(int i=0; i<MAX_EPISODES; i++){
            // 初始状态
            NDArray coor = manager.create(new float[]{1.0f, 1.0f}).expandDims(0);
            NDArray dist_s = goal.sub(coor);
            NDArray s = coor.concat(dist_s, 1);
            float ep_r = 0.0f;
            for (int j=0; j<MAX_EP_STEPS; j++){
                NDArray a = ddpg.choose_action(s);
//                System.out.println(a);
                NDArray coor_ = coor.add(a.mul(0.1));
//                System.out.println(s_);
//                System.out.println(s.sub(goal).square().mean().sqrt());
                NDArray dist_s_ = goal.sub(coor_);
                NDArray s_ = coor_.concat(dist_s_, 1);

                NDArray r = dist_s_.square().mean().sqrt().neg().expandDims(0);
//                NDArray r = manager.create(new float[]{ -dist_s_ + dist_s}).expandDims(0);

//                if (dist_s_ < 0.1) {
//                    r.set(new NDIndex("0"), 1.0f);
//                }

                ddpg.store_transition(s, a, r.expandDims(0), s_);
                ep_r += r.getFloat(0);
                if(ddpg.memory_full){
                    ddpg.learn();
                }

                s = s_;
                coor = coor_;

//                if (s.get(0).getFloat(0) < 0.0f || s.get(0).getFloat(1) < 0.0f) {
//                    System.out.println("Game failed!  Reward: " + ep_r + " Steps: " + j);
//                    break;
//                }
                if(goal.getFloat(0) - 0.1 < coor_.get(0).getFloat(0)
                        && goal.getFloat(0) + 0.1 > coor_.get(0).getFloat(0)
                        && goal.getFloat(1) - 0.1 < coor_.get(0).getFloat(1)
                        && goal.getFloat(1) + 0.1 > coor_.get(0).getFloat(1)) {
                    System.out.println("Game Success! Reward: " + ep_r + " Steps: " + j + " ("+ s.get(0)+")");
                    break;
                } else if(j == MAX_EP_STEPS - 1){
                    System.out.println("Game failed!  Reward: " + ep_r + " Steps: " + j + " ("+ s.get(0)+")");
                    break;
                }
            }
        }
    }

    public static void main(String[] args){

        DDPG ddpg = new DDPG(2, 4, new Pair<Float, Float>(-1.0f, 1.0f));

        train(ddpg);

//        NDManager manager = NDManager.newBaseManager();
//
//        NDArray a = manager.ones(new Shape(100));
//
//        NDArray b = a.split(new long[]{0, 20}).get(1);
//        System.out.println(b);
//
//        NDArray c = manager.ones(new Shape(2, 2));
//        NDArray d = manager.ones(new Shape(3, 2));
//        c = c.concat(d, 0);
//        NDArray indices = manager.ones(new Shape(5), DataType.BOOLEAN);
//        indices.set(new NDIndex("1"), 0);
//        System.out.println(indices);
//        System.out.println(c);
//        System.out.println(c.get(indices));
//
//        NDArray m = manager.arange(10);
//        System.out.println(m.get("-3:-2"));
    }

    static class MyBlock extends AbstractBlock {
        private static final byte VERSION = 1;
        private Block w1_s;
        private Block w1_a;
        private Parameter b1;
        private int s_dim;
        private int a_dim;

        public MyBlock(int s_dim, int a_dim) {
            super(VERSION);

            w1_s = addChildBlock("w1_s", Linear.builder().setUnits(100).build());
            w1_a = addChildBlock("w1_a", Linear.builder().setUnits(100).build());
            b1 = addParameter(Parameter.builder().optShape(new Shape(1, 100)).setName("b1").setType(Parameter.Type.BIAS).build());
            this.s_dim = s_dim;
            this.a_dim = a_dim;
        }


        @Override
        protected NDList forwardInternal(ParameterStore parameterStore, NDList inputs, boolean training, PairList<String, Object> params) {
            NDArray input = inputs.singletonOrThrow();
            NDArray s = input.get(":, :{}", this.s_dim);
            NDArray a = input.get(":, {}:", -this.a_dim);

            NDArray _w1_s = w1_s.forward(parameterStore, new NDList(s), training).singletonOrThrow();
            NDArray _w1_a = w1_a.forward(parameterStore, new NDList(a), training).singletonOrThrow();
            NDArray _b1 = parameterStore.getValue(this.b1, input.getDevice(), training);

            NDArray net = Activation.relu(_w1_s.add(_w1_a).add(_b1));
            return new NDList(net);
        }

        @Override
        public Shape[] getOutputShapes(Shape[] inputs) {
            return new Shape[]{new Shape(1, 100)};
        }

        @Override
        public void initializeChildBlocks(NDManager manager, DataType dataType, Shape... inputShapes) {
            w1_s.initialize(manager, dataType, new Shape(1, s_dim));
            w1_a.initialize(manager, dataType, new Shape(1, a_dim));
        }
    }
}
