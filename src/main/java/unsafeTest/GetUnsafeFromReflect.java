package unsafeTest;

import sun.misc2.Unsafe;

import java.lang.reflect.Field;

public class GetUnsafeFromReflect {
    public static void main(String[] args){
        Unsafe unsafe = getUnsafe();
        System.out.printf("addressSize=%d, pageSize=%d\n", unsafe.addressSize(), unsafe.pageSize());
    }
 
    public static Unsafe getUnsafe() {
        Unsafe unsafe = null;
        try {
            Field f = Unsafe.class.getDeclaredField("theUnsafe");
            f.setAccessible(true);
            unsafe = (Unsafe) f.get(null);
        } catch (NoSuchFieldException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        }
        return unsafe;
    }
}