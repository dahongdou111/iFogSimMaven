package org.fog.test.perfeval.ceph.crush;

public class Hash {

    static final int CRUSH_HASH_RJENKINS1 = 0;

    static final int crush_hash_seed = 1315423911;

    static void crush_hashmix(Integer a, Integer b, Integer c){
        do {
            a = a-b;  a = a-c;  a = a^(c>>13);
            b = b-c;  b = b-a;  b = b^(a<<8);
            c = c-a;  c = c-b;  c = c^(b>>13);
            a = a-b;  a = a-c;  a = a^(c>>12);
            b = b-c;  b = b-a;  b = b^(a<<16);
            c = c-a;  c = c-b;  c = c^(b>>5);
            a = a-b;  a = a-c;  a = a^(c>>3);
            b = b-c;  b = b-a;  b = b^(a<<10);
            c = c-a;  c = c-b;  c = c^(b>>15);
        } while(false);
    }

    static int crush_hash32_rjenkins1_3(int a, int b, int c){
        Integer hash = crush_hash_seed ^ a ^ b ^ c;
        Integer x = 231232;
        Integer y = 1232;
        crush_hashmix(a, b, hash);
        crush_hashmix(c, x, hash);
        crush_hashmix(y, a, hash);
        crush_hashmix(b, x, hash);
        crush_hashmix(y, c, hash);
        return hash;
    }

    public static int crush_hash32_3(int type, int a, int b, int c){
        switch (type) {
            case CRUSH_HASH_RJENKINS1:
                return crush_hash32_rjenkins1_3(a, b, c);
            default:
                return 0;
        }
    }
}
