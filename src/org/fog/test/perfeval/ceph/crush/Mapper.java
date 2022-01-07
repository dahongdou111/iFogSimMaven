package org.fog.test.perfeval.ceph.crush;


import com.sun.xml.internal.fastinfoset.tools.FI_SAX_Or_XML_SAX_DOM_SAX_SAXEvent;
import org.cloudbus.cloudsim.Log;

import static org.fog.test.perfeval.ceph.crush.Crush.Crush_rule_step.CRUSH_RULE_CHOOSELEAF_INDEP;
import static org.fog.test.perfeval.ceph.crush.Crush.crush_algorithm.CRUSH_BUCKET_STRAW;

public class Mapper {
    /**
     * crush_do_rule - calculate a mapping with the given input and rule
     * @param map crush map结构
     * @param ruleno ruleset的号
     * @param x 输入，一般是pg的id
     * @param result 输出osd列表
     * @param result_max 输出osd列表的数量
     * @param weight 所有osd的权重，通过它来判断osd是否out
     * @param weight_max 所有osd的数量
     * @return
     */
    int crush_do_rule(Crush.Crush_map map,
                      int ruleno, int x, int[] result, int result_max,
                      int[] weight, int weight_max)
    {
        int wsize = 0;  // 什么含义


        Crush.Crush_rule rule = map.rules[ruleno];
        for (int step = 0; step < rule.len; step++){
            boolean firstn = true;
            int[] w = new int[5];
            Crush.Crush_rule_step curstep = rule.steps[step];

            switch (curstep.op) {
                case CRUSH_RULE_CHOOSELEAF_INDEP:
                    if (wsize == 0)
                        break;

                    int osize = 0;  // 重新设置输出？

                    for (int i=0; i<wsize; i++){
                        int bno = 0;
                        int numrep = curstep.arg1;
                        if (numrep <= 0) {
                            numrep += result_max; //？
                            if (numrep <= 0)
                                continue;
                        }
                        int j=0;

                        // make sure bucket id is valid
                        bno = -1 - w[i];
                        if (bno < 0 || bno >= map.max_buckets) {
                            Log.printLine(String.format("  bad w[i] %d\n", w[i]));
                            continue;
                        }
                        if (firstn) {

                        }
                    }
            }
        }

        return result_len;
    }

    int crush_choose_firstn(Crush.Crush_map map,
                            Crush.Crush_work work,
                            Crush.Crush_bucket bucket,
                            int[] weight, int weight_max,
                            int x, int numrep, int type,
                            int[] out, int outpos,
                            int out_size,
                            int tries,
                            int recurse_tries,
                            int local_retries,
                            int local_fallback_retries,
                            boolean recurse_to_leaf,
                            int vary_r,
                            int stable,
                            int[] out2,
                            int parent_r,
                            Crush.Crush_choose_arg[] choose_args)
    {

    }

    int bucket_perm_choose(Crush.Crush_bucket bucket, Crush.Crush_work_bucket work, int x, int r){
        int pr = r % bucket.size;
        int i, s;

        if (work.perm_x != x || work.perm_n == 0){
            Log.printLine(String.format("bucket %d new x=%d", bucket.id, x));
            work.perm_x = x;

            if (pr == 0) {
                s = Hash.crush_hash32_3(bucket.hash, x, bucket.id, 0) % bucket.size;
                work.perm[0] = s;
                work.perm_n = 0xffff;
                Log.printLine(String.format(" perm_choose %d sz=%d x=%d r=%d (%d) s=%d", bucket.id,
                        bucket.size, x, r, pr, s));
                return bucket.items[s];
            }

            for (i = 0; i < bucket.size; i++)
                work.perm[i] = i;
            work.perm_n = 0;
        } else if (work.perm_n == 0xffff) {
            /** clean up after the r=0 case above */
            for (i = 1; i < bucket.size; i++)
                work.perm[i] = i;
            work.perm[work.perm[0]] = 0;
            work.perm_n = 1;
        }

        /** calculate permutation up to pr */
        for (i = 0; i < work.perm_n; i++)
            Log.printLine(String.format(" perm_choose have %d: %d", i, work.perm[i]));
        while (work.perm_n <= pr) {
            int p = work.perm_n;
            if (p < bucket.size - 1){
                /** 交换？ */
                i = Hash.crush_hash32_3(bucket.hash, x, bucket.id, p) % (bucket.size - p);
                if(i != 0){
                    int t = work.perm[p + i];
                    work.perm[p + i] = work.perm[p];
                    work.perm[p] = t;
                }
                Log.printLine(String.format(" perm_choose swap %d with %d", p, p+i));
            }
            work.perm_n++;
        }
        for (i = 0; i < bucket.size; i++)
            Log.printLine(String.format(" perm_choose %d: %d", i, work.perm[i]));

        s = work.perm[pr];
        /** 源码有个goto语法在此 */
        Log.printLine(String.format(" perm_choose %d sz=%d x=%d r=%d (%d) s=%d", bucket.id,
                bucket.size, x, r, pr, s));
        return bucket.items[s];
    }

    int crush_bucket_choose(Crush.Crush_bucket in, Crush.Crush_work_bucket work, int x, int r,
                            Crush.Crush_choose_arg arg, int position)
    {
        Log.printLine(String.format(" crush_bucket_choose %d x=%d r=%d", in.id, x, r));
        assert(in.size != 0);
        switch (in.alg) {
            case Crush.crush_algorithm.CRUSH_BUCKET_STRAW:
                return bucket_straw_choose(new Crush.Crush_bucket_straw(in), x, r);
            default:
                Log.printLine(String.format("unknown bucket %d alg %d", in.id, in.alg));
                return in.items[0];
        }
    }

    int bucket_straw_choose(Crush.Crush_bucket_straw bucket, int x, int r){
        int i;
        int high = 0;
        long high_draw = 0;
        long draw;

        for (i = 0; i < bucket.h.size; i++){
            draw = Hash.crush_hash32_3(bucket.h.hash, x, bucket.h.items[i], r);
            draw &= 0xffff;
            draw *= bucket.straws[i];
            if (i == 0 || draw > high_draw) {
                high = i;
                high_draw = draw;
            }
        }
        return bucket.h.items[high];
    }
}
