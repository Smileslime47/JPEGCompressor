package moe._47saikyo.test;
import moe._47saikyo.JpegCompressor;
import org.junit.Before;
import org.junit.Test;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public class JpegCompressorTest {
    private static JpegCompressor comp;
    private int imageHeight;
    private int imageWidth;
    private int completionWidth;
    private int completionHeight;
    private final int MCULength = 16;
    private final int blockLength = MCULength / 2;

    @Before
    public void initTest() throws FileNotFoundException, NoSuchFieldException, IllegalAccessException {
        comp = new JpegCompressor(new FileInputStream("res/肖像.png"), null);

        Field iH = JpegCompressor.class.getDeclaredField("imageHeight");
        iH.setAccessible(true);
        imageHeight = (int) iH.get(comp);
        Field iW = JpegCompressor.class.getDeclaredField("imageWidth");
        iW.setAccessible(true);
        imageWidth = (int) iW.get(comp);
        Field cH = JpegCompressor.class.getDeclaredField("completionHeight");
        cH.setAccessible(true);
        completionHeight = (int) cH.get(comp);
        Field cW = JpegCompressor.class.getDeclaredField("completionWidth");
        cW.setAccessible(true);
        completionWidth = (int) cW.get(comp);
    }

    @Test
    public void allFunctionTest() throws IOException {
        comp.setOutput(new FileOutputStream("res/out1.jpg"));
        comp.doCompress();
    }

    @Test
    public void downSamplingTest() throws NoSuchMethodException, InvocationTargetException, IllegalAccessException, NoSuchFieldException {
        Method getMCUBlock = JpegCompressor.class.getDeclaredMethod("getMCUBlock", int.class, int.class);
        getMCUBlock.setAccessible(true);
        float[][][] Array;
        int x,y;
        //分块序号，x和y标记当前行/列的第几个MCU
        for(y=0;y*MCULength+MCULength<=completionHeight;y++){
            for (x=0;x*MCULength+MCULength<=completionWidth;x++){

                Array= (float[][][]) getMCUBlock.invoke(comp,x,y);
                deBugWrite(Array,x,y);
            }
        }
        comp.deBugWrite("res/downSampling-out.bmp");
    }

    @Test
    public void DCTTest() throws NoSuchMethodException, InvocationTargetException, IllegalAccessException, NoSuchFieldException {
        Method DCTComp_L = JpegCompressor.class.getDeclaredMethod("DCTComp_L", float[][].class);
        Method DCTComp_C = JpegCompressor.class.getDeclaredMethod("DCTComp_C", float[][].class);
        Method IDCT_L = JpegCompressor.class.getDeclaredMethod("IDCT_L", int[][].class);
        Method IDCT_C = JpegCompressor.class.getDeclaredMethod("IDCT_C", int[][].class);
        Method getMCUBlock = JpegCompressor.class.getDeclaredMethod("getMCUBlock", int.class, int.class);
        getMCUBlock.setAccessible(true);
        DCTComp_C.setAccessible(true);
        DCTComp_L.setAccessible(true);
        IDCT_C.setAccessible(true);
        IDCT_L.setAccessible(true);
        float[][][] Array;
        int[][] tmp;
        int x,y;
        //分块序号，x和y标记当前行/列的第几个MCU
        for(y=0;y*MCULength+MCULength<=completionHeight;y++){
            for (x=0;x*MCULength+MCULength<=completionWidth;x++){

                Array= (float[][][]) getMCUBlock.invoke(comp,x,y);
                for (int i=0;i<4;i++){
                    tmp= (int[][]) DCTComp_L.invoke(comp, (Object) Array[i]);
                    Array[i]= (float[][]) IDCT_L.invoke(comp, (Object) tmp);
                }
                tmp=(int[][])DCTComp_C.invoke(comp, (Object) Array[4]);
                Array[4]=(float[][])IDCT_C.invoke(comp, (Object) tmp);
                tmp=(int[][])DCTComp_C.invoke(comp, (Object) Array[5]);
                Array[5]=(float[][])IDCT_C.invoke(comp, (Object) tmp);
                deBugWrite(Array,x,y);
            }
        }
        comp.deBugWrite("res/DCT-out.bmp");
    }

    @Test
    public void luminanceTest() throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        Method getMCUBlock = JpegCompressor.class.getDeclaredMethod("getMCUBlock", int.class, int.class);
        getMCUBlock.setAccessible(true);
        float[][][] Array;
        int x,y;
        //分块序号，x和y标记当前行/列的第几个MCU
        for(y=0;y*MCULength+MCULength<=completionHeight;y++){
            for (x=0;x*MCULength+MCULength<=completionWidth;x++){

                Array= (float[][][]) getMCUBlock.invoke(comp,x,y);
                for (int i=4;i<6;i++){
                    for (int r=0;r<blockLength;r++){
                        for (int c=0;c<blockLength;c++){
                            Array[i][r][c]=128;
                        }
                    }
                }
                deBugWrite(Array,x,y);
            }
        }
        comp.deBugWrite("res/luminance-out.bmp");
    }
    @Test
    public void chrominanceTest() throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        Method getMCUBlock = JpegCompressor.class.getDeclaredMethod("getMCUBlock", int.class, int.class);
        getMCUBlock.setAccessible(true);
        float[][][] Array;
        int x,y;
        //分块序号，x和y标记当前行/列的第几个MCU
        for(y=0;y*MCULength+MCULength<=completionHeight;y++){
            for (x=0;x*MCULength+MCULength<=completionWidth;x++){

                Array= (float[][][]) getMCUBlock.invoke(comp,x,y);
                for (int i=0;i<4;i++){
                    for (int r=0;r<blockLength;r++){
                        for (int c=0;c<blockLength;c++){
                            Array[i][r][c]=128;
                        }
                    }
                }
                deBugWrite(Array,x,y);
            }
        }
        comp.deBugWrite("res/chrominance-out.bmp");
    }

    public void deBugWrite(float[][][] Array,int x,int y) throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        Method setRGB = JpegCompressor.class.getDeclaredMethod("setRGB", int.class, int.class, float[].class);
        setRGB.setAccessible(true);
        float[] RGB=new float[3];
        float[] YCC=new float[3];
        int i,xOffset,yOffset,r,c,MCU_r_offset,MCU_c_offset;
        for (i=0;i<4;i++){
            //根据序号获取块的起始偏移值
            xOffset=x*MCULength;
            yOffset=y*MCULength;
            MCU_r_offset=0;
            MCU_c_offset=0;
            if(i==1||i==3){
                xOffset+=blockLength;
                MCU_c_offset+=blockLength;
            }
            if(i==2||i==3){
                yOffset+=blockLength;
                MCU_r_offset+=blockLength;
            }
            for (r=0;r<blockLength;r++){
                for (c=0;c<blockLength;c++){
                    if(xOffset+c>=imageWidth||yOffset+r>=imageHeight){
                        continue;
                    }
                    YCC[0]=Array[i][r][c];
                    YCC[1]=Array[4][(r+MCU_r_offset)/2][(c+MCU_c_offset)/2];
                    YCC[2]=Array[5][(r+MCU_r_offset)/2][(c+MCU_c_offset)/2];
                    RGB[0]=(float) (YCC[0] + 1.402 * (YCC[2] - 128));
                    RGB[1]=(float) (YCC[0] - 0.34414 * (YCC[1] - 128) - 0.71414 * (YCC[2] - 128));
                    RGB[2]= (float) (YCC[0] + 1.772 * (YCC[1] - 128));
                    setRGB.invoke(comp,xOffset+c,yOffset+r,RGB);
                }
            }
        }
    }
}
