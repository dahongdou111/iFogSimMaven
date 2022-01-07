package org.fog.test.perfeval.ceph.crush;

import java.util.List;

public class Crush {
    public static class Crush_map {
        Crush_bucket[] buckets;
        Crush_rule[] rules;

        int max_buckets;
        int max_rules;
        int max_devices;

        int choose_total_tries;
        int choose_local_tries;
        int choose_local_fallback_tries;

        int chooseleaf_descend_once;

        int chooseleaf_vary_r;
        int chooseleaf_stable;

        long working_size;
    }

    public static class Crush_bucket {
        int id;                             //bucket的id，一般为负数
        int type;                           //类型，如果是0，就是OSD设备
        int alg;                            //bucket的选择算法
        int hash;                           //bucket的hash函数
        int weight;                         //bucket的权重
        int size;                           //bucket下的item的数量
        Integer[] items;
    }

    public static class Crush_work_bucket {
        int perm_x;
        int perm_n;
        Integer[] perm;
    }

    public static class Crush_work {
        Crush_work_bucket[] work;
    }

    /**
     *
     */
    public static class Crush_rule {
        int len;                            //steps的数组的长度
        int __unused_was_rule_mask_ruleset; //ruleset的编号
        int type;                           //类型
        int deprecated_min_size;            //最新size
        int deprecated_max_size;            //最大size
        Crush_rule_step[] steps;        //操作步
    }

    public static class Crush_rule_step{
        int op;                             //step操作步的操作码
        int arg1;                           //如果是take，参数就是选择的bucket的id号；如果是select，就是选择的数量
        int arg2;                           //如果是select，是选择的类型

        /**
         * crush rule op 枚举值
         */
        public static final int CRUSH_RULE_NOOP = 0;
        public static final int CRUSH_RULE_TAKE = 1;
        public static final int CRUSH_RULE_CHOOSE_FIRSTN = 2;
        public static final int CRUSH_RULE_CHOOSE_INDEP = 3;
        public static final int CRUSH_RULE_EMIT = 4;
        public static final int CRUSH_RULE_CHOOSELEAF_FIRSTN = 6;
        public static final int CRUSH_RULE_CHOOSELEAF_INDEP = 7;

        public static final int CRUSH_RULE_SET_CHOOSE_TRIES = 8;
        public static final int CRUSH_RULE_SET_CHOOSELEAF_TRIES = 9;
        public static final int CRUSH_RULE_SET_CHOOSE_LOCAL_TRIES = 10;
        public static final int CRUSH_RULE_SET_CHOOSE_LOCAL_FALLBACK_TRIES = 11;
        public static final int CRUSH_RULE_SET_CHOOSELEAF_VARY_R = 12;
        public static final int CRUSH_RULE_SET_CHOOSELEAF_STABLE = 13;
    }

    public static class Crush_weight_set {
        int[] weight;
        int size;
    }

    public static class Crush_choose_arg {
        int[] ids;
        int ids_size;
        Crush_weight_set weight_set;
        int weight_set_positions;
    }

    public static class Crush_bucket_straw {
        Crush_bucket h;
        int[] item_weights;
        int[] straws;

        public Crush_bucket_straw(){}
        public Crush_bucket_straw(Crush_bucket h){this.h = h;}
    }



    public static class crush_algorithm {
        static final int CRUSH_BUCKET_UNIFORM = 1;
        static final int CRUSH_BUCKET_LIST = 2;
        static final int CRUSH_BUCKET_TREE = 3;
        static final int CRUSH_BUCKET_STRAW = 4;
        static final int CRUSH_BUCKET_STRAW2 = 5;
    }
}
