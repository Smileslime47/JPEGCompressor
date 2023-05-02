package moe._47saikyo;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.List;

/**
 * 一个Jpeg压缩编码器，也是我的数字图形处理结课作业，仅供学习用途
 * JPEG Compressor Copyright 2023 Smile_slime_47
 * @author Smile_slime_47
 * @version 1.0.0-alpha
 */
public class JpegCompressor {
    //test
    public static void main(String[] args) throws IOException {
        JpegCompressor comp = new JpegCompressor(new FileInputStream("knowledge.bmp"),new FileOutputStream("out.jpg"));
        comp.doCompress();
        //io.debugWrite("C:\\Users\\smile\\Pictures\\testout0.bmp");
    }

    private JpegIOStream            IO;
    private BufferedImage           image;
    private DCT                     dct;
    private EntropyEncoder          entropy;
    private int                     imageHeight;
    private int                     imageWidth;
    private int                     completionWidth;
    private int                     completionHeight;
    private int                     MCULength=16;
    private final int               blockLength=MCULength/2;
    //private final int               compLength=blockLength/2;

    public  ColorComponentHandler   RgbToYccHandler;
    public  ColorComponentHandler   YccToRgbHandler;

    /*
     * 我给它起名为通道转换器（ColorComponentHandler），用于将一个float格式的色彩通道数组转换成另一种格式
     */
    interface ColorComponentHandler {
        float[] get(float[] components);
    }

    public JpegCompressor(InputStream input,OutputStream output){
        IO=new JpegIOStream(input,output);
        initJpegCompressor();
    }
    public JpegCompressor(JpegIOStream io) {
        IO=io;
        initJpegCompressor();
    }
    private void initJpegCompressor(){
        image = IO.getImage();
        imageWidth = IO.imageWidth;
        imageHeight = IO.imageHeight;
        dct=new DCT();
        entropy=new EntropyEncoder(IO.getOutput());
        //对于图像尺寸不满足16的倍数的，补全到16的倍数，方便后面进行分块
        completionWidth = ((imageWidth % MCULength != 0) ? (int) (Math.floor((double) imageWidth / MCULength) + 1) * MCULength : imageWidth);
        completionHeight = ((imageHeight % MCULength != 0) ? (int) (Math.floor((double) imageHeight / MCULength) + 1) * MCULength : imageHeight);

        //定义RGB 2 YCC通道转换器
        RgbToYccHandler = (RGB) -> new float[]{
                (float) ((0.299 * RGB[0] + 0.587 * RGB[1] + 0.114 * RGB[2])),
                (float) ((-0.16874 * RGB[0] - 0.33126 * RGB[1] + 0.5 * RGB[2])) + 128,
                (float) ((0.5 * RGB[0] - 0.41869 * RGB[1] - 0.08131 * RGB[2])) + 128
        };

        //定义YCC 2 RGB通道转换器
        YccToRgbHandler = (YUV) -> new float[]{
                (float) (YUV[0] + 1.402 * (YUV[2] - 128)),
                (float) (YUV[0] - 0.34414 * (YUV[1] - 128) - 0.71414 * (YUV[2] - 128)),
                (float) (YUV[0] + 1.772 * (YUV[1] - 128))
        };
    }
    public void doCompress() throws IOException {
        IO.writeHeader();
        WriteCompressedData();
        IO.writeEOI();
    }
    public void setComment(String comment){
        IO.setComment(comment);
    }

//  void WriteCompressedData(BufferedOutputStream outStream) throws IOException {
    private void WriteCompressedData() throws IOException {
        float[] RGB;
        float[] YCC = new float[0];
        float[][][] YArray=new float[4][blockLength][blockLength];
        float[][] UArray=new float[blockLength][blockLength];
        float[][] VArray=new float[blockLength][blockLength];

        int[][][] DCTYArray=new int[4][blockLength][blockLength];
        int[][] DCTUArray=new int[blockLength][blockLength];
        int[][] DCTVArray=new int[blockLength][blockLength];

        int i,x,y,xOffset,yOffset,r,c,MCU_r_offset,MCU_c_offset;
        //分块序号，x和y标记当前行/列的第几个MCU
        for(y=0;y*MCULength+MCULength<=completionHeight;y++){
            for (x=0;x*MCULength+MCULength<=completionWidth;x++){

                //生成Y U V矩阵
                for (i=0;i<4;i++){
                    //x/yOffset用去获取当前像素点在图像中的绝对位置
                    xOffset=x*MCULength;
                    yOffset=y*MCULength;
                    //r/cOffset用于获取当前像素点在MCU中的相对位置
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
                                //边缘色度填充，防止出现振铃效应
                                RGB=getRGB(xOffset+c>=imageWidth?imageWidth-1:xOffset+c,yOffset+r>=imageHeight?imageHeight-1:yOffset+r);
                            }else{
                                RGB=getRGB(xOffset+c,yOffset+r);
                            }

                            YCC=RgbToYccHandler.get(RGB);
//                            //色度图
//                            YCC[0]=128;
//                            //灰度图
//                            YCC[1]=128;
//                            YCC[2]=128;
                            YArray[i][r][c]=YCC[0];

                            //downSampling 色度抽样，偶数行采样U，奇数行采样V，压缩的第一步，将每4个像素共12组数据压缩为6组
                            if((r+MCU_r_offset)%2==0&&(c+MCU_c_offset)%2==0){
                                UArray[(r+MCU_r_offset)/2][(c+MCU_c_offset)/2]=YCC[1];
                            }
                            else if((r+MCU_r_offset)%2==1&&(c+MCU_c_offset)%2==0){
                                VArray[(r+MCU_r_offset)/2][(c+MCU_c_offset)/2]=YCC[2];
                            }
                        }
                    }
                }

//                //色度抽样输出测试
//                for (i=0;i<4;i++){
//                    //根据序号获取块的起始偏移值
//                    xOffset=x*MCULength;
//                    yOffset=y*MCULength;
//                    MCU_r_offset=0;
//                    MCU_c_offset=0;
//                    if(i==1||i==3){
//                        xOffset+=blockLength;
//                        MCU_c_offset+=blockLength;
//                    }
//                    if(i==2||i==3){
//                        yOffset+=blockLength;
//                        MCU_r_offset+=blockLength;
//                    }
//                    for (r=0;r<blockLength;r++){
//                        for (c=0;c<blockLength;c++){
//                            if(xOffset+c>=imageWidth||yOffset+r>=imageHeight){
//                                continue;
//                            }
//                            YCC[0]=YArray[i][r][c];
//                            YCC[1]=UArray[(r+MCU_r_offset)/2][(c+MCU_c_offset)/2];
//                            YCC[2]=VArray[(r+MCU_r_offset)/2][(c+MCU_c_offset)/2];
//                            RGB=YccToRgbHandler.get(YCC);
//                            setRGB(xOffset+c,yOffset+r,RGB);
//                        }
//                    }
//                }


                //DCT转换
                for (i=0;i<4;i++){
                    dct.initMatrix(YArray[i]);
                    dct.forwardDCT();
                    DCTYArray[i]=dct.quantize(DCT.component.luminance);
                }
                dct.initMatrix(UArray);
                dct.forwardDCT();
                DCTUArray=dct.quantize(DCT.component.chrominance);
                dct.initMatrix(VArray);
                dct.forwardDCT();
                DCTVArray=dct.quantize(DCT.component.chrominance);

//                //DCT输出测试
//                if(x==20&&y==20){
//                    for(int i=0;i<compLength;i++){
//                        for (int j=0;j<compLength;j++){
//                            System.out.print(DCTUArray[i][j]+"\t\t");
//                        }
//                        System.out.println();
//                    }
//                }

//                //量化输出测试
//                for (i=0;i<4;i++){
//                    dct.initMatrix(DCTYArray[i]);
//                    dct.reverseQuantize(DCT.component.luminance);
//                    YArray[i]=dct.reverseDCT();
//                }
//                dct.initMatrix(DCTUArray);
//                dct.reverseQuantize(DCT.component.chrominance);
//                UArray=dct.reverseDCT();
//                dct.initMatrix(DCTVArray);
//                dct.reverseQuantize(DCT.component.chrominance);
//                VArray=dct.reverseDCT();
//                for (i=0;i<4;i++){
//                    //根据序号获取块的起始偏移值
//                    xOffset=x*MCULength;
//                    yOffset=y*MCULength;
//                    MCU_r_offset=0;
//                    MCU_c_offset=0;
//                    if(i==1||i==3){
//                        xOffset+=blockLength;
//                        MCU_c_offset+=blockLength;
//                    }
//                    if(i==2||i==3){
//                        yOffset+=blockLength;
//                        MCU_r_offset+=blockLength;
//                    }
//                    for (r=0;r<blockLength;r++){
//                        for (c=0;c<blockLength;c++){
//                            if(xOffset+c>=imageWidth||yOffset+r>=imageHeight){
//                                continue;
//                            }
//                            YCC[0]=YArray[i][r][c];
//                            YCC[1]=UArray[(r+MCU_r_offset)/2][(c+MCU_c_offset)/2];
//                            YCC[2]=VArray[(r+MCU_r_offset)/2][(c+MCU_c_offset)/2];
//                            RGB=YccToRgbHandler.get(YCC);
//                            setRGB(xOffset+c,yOffset+r,RGB);
//                        }
//                    }
//                }

                //熵编码
                for (i=0;i<4;i++){
                    entropy.initMatrix(DCTYArray[i]);
                    entropy.writeHuffmanBits(EntropyEncoder.component.Y);
                }

                entropy.initMatrix(DCTUArray);
                entropy.writeHuffmanBits(EntropyEncoder.component.Cb);

                entropy.initMatrix(DCTVArray);
                entropy.writeHuffmanBits(EntropyEncoder.component.Cr);
            }
        }
        entropy.flushByte();
    }


    /*
     * 获取和写入RGB色彩通道
     * 写入方法主要是用作调试
     */
    private float[] getRGB(int x, int y) {
        if (x >= imageWidth) {
            x -= imageWidth;
            y++;
        }
        if (y >= imageHeight) {
            return null;
        }
        int value = image.getRGB(x, y);
        int r = ((value >> 16) & 0xff);
        int g = ((value >> 8) & 0xff);
        int b = (value & 0xff);
        return new float[]{r, g, b};
    }
    private void setRGB(int x, int y,float[] RGBComponent){
        //YCC转回RGB时有可能会超出[0,255]，需要判断一下，血的教训（
        for (int i=0;i<3;i++){
            if(RGBComponent[i]<0)RGBComponent[i]=0;
            if(RGBComponent[i]>255)RGBComponent[i]=255;
        }
        int rgb=((int) RGBComponent[0] << 16) | ((int) RGBComponent[1] << 8) | (int) (RGBComponent[2]);
        image.setRGB(x, y, rgb);
    }
}

//文件头标记位表
class JPEGHeader {
    public static final byte    marker  = (byte) 0xFF;
    public static final byte    SOI     = (byte) 0xD8;
    public static final byte    SOF0    = (byte) 0xC0;
    public static final byte    SOF2    = (byte) 0xC2;
    public static final byte    DHT     = (byte) 0xC4;
    public static final byte    DQT     = (byte) 0xDB;
    public static final byte    DRI     = (byte) 0xDD;
    public static final byte    SOS     = (byte) 0xDA;
    public static final byte[]  RSTn    ={(byte) 0xD1, (byte) 0xD2, (byte) 0xD3, (byte) 0xD4, (byte) 0xD5, (byte) 0xD6, (byte) 0xD7};
    public static final byte    COM     = (byte) 0xFE;
    public static final byte    EOI     = (byte) 0xD9;
}
class JpegIOStream {
    private BufferedImage bufferedImage;
    private BufferedOutputStream bufferedOutput;
    public int imageHeight;
    public int imageWidth;
    private byte[] comment;
    private final String defaultComment="JPEG Compressor Copyright 2023 Smile_slime_47";

    public JpegIOStream(File input, OutputStream output) {
        try {
            bufferedImage = ImageIO.read(input);
        } catch (IOException ignored) {
        }
        this.imageWidth = bufferedImage.getWidth();
        this.imageHeight = bufferedImage.getHeight();
        comment=defaultComment.getBytes();
        bufferedOutput=new BufferedOutputStream(output);
    }
    public JpegIOStream(InputStream input, OutputStream output) {
        try {
            bufferedImage = ImageIO.read(input);
        } catch (IOException ignored) {
        }
        this.imageWidth = bufferedImage.getWidth();
        this.imageHeight = bufferedImage.getHeight();
        comment=defaultComment.getBytes();
        bufferedOutput=new BufferedOutputStream(output);
    }

    public BufferedOutputStream getOutput(){return bufferedOutput;}
    public BufferedImage getImage() {
        return bufferedImage;
    }

    public void debugWrite(String File) {
        try {
            ImageIO.write(bufferedImage, "bmp", new File(File));
        } catch (IOException ignored) {
        }
    }

    public void setComment(String com){
        comment=com.getBytes();
    }
    private void writeMarker(byte marker) throws IOException {
        bufferedOutput.write(JPEGHeader.marker);
        bufferedOutput.write(marker);
    }
    private void writeByte(byte data) throws IOException {
        bufferedOutput.write(data);
    }
    private void writeArray(byte[] dataArr) throws IOException {
        bufferedOutput.write(dataArr);
    }
    public void writeEOI() throws IOException {
        writeMarker(JPEGHeader.EOI);
        bufferedOutput.flush();
    }
    public void writeHeader() throws IOException {
        int[] zigzagDQT;
        int[] bitsDHT,valDHT;
        //Start Of Image
        writeMarker(JPEGHeader.SOI);

        //APP0_JFIF文件头
        writeMarker((byte) 0xE0);
        byte[] JFIFPayload={
                //标记码长度——16
                0x00,
                0x10,
                //"JFIF"标记
                0x4A,
                0x46,
                0x49,
                0x46,
                0x00,
                //JFIF版本号_01.01
                0x01,
                0x01,
                //坐标单位——0：无单位；1：英寸：2：厘米
                0x00,
                //水平/垂直分辨率
                0x00,
                0x01,
                0x00,
                0x01,
                //thumbnail分辨率
                0x00,
                0x000
        };
        writeArray(JFIFPayload);

        //Comment_图片注释
        writeMarker(JPEGHeader.COM);
        //Comment长度
        writeByte((byte) ((comment.length>>8)&0xFF));
        writeByte((byte) ((comment.length)&0xFF));
        //写入Comment
        writeArray(comment);

        //DQT_亮度量化表
        writeMarker(JPEGHeader.DQT);
        zigzagDQT=EntropyEncoder.zigzagScan(DCT.quantum_luminance);
        //DQT标记段长度
        writeByte((byte) 0x00);
        writeByte((byte) 0x43);
        //高四位：精度——0为1byte、1为2byte；低四位：量化表ID——0~3
        writeByte((byte) 0x00);
        for (int i:zigzagDQT){
            writeByte((byte) i);
        }

        //DQT_亮度量化表
        writeMarker(JPEGHeader.DQT);
        zigzagDQT=EntropyEncoder.zigzagScan(DCT.quantum_chrominance);
        //DQT标记段长度
        writeByte((byte) 0x00);
        writeByte((byte) 0x43);
        //高四位：精度——0为1byte、1为2byte；低四位：量化表ID——0~3
        writeByte((byte) 0x01);
        for (int i:zigzagDQT){
            writeByte((byte) i);
        }

        //Start Of Frame，图像基本信息
        writeMarker(JPEGHeader.SOF0);
        byte[] SOF0Payload={
                //标记段长度_17
                0x00,
                0x11,
                //图片精度（位深）
                0x08,
                //图片高度
                (byte) ((imageHeight>>8)&0xFF),
                (byte) ((imageHeight)&0xFF),
                //图片宽度
                (byte) ((imageWidth>>8)&0xFF),
                (byte) ((imageWidth)&0xFF),
                //色彩通道数
                0x03,
                //通道ID1_Y通道
                0x01,
                //采样系数：高四位：水平；低四位：垂直
                (2<<4)+2,
                //量化表ID
                0x00,
                //通道ID2_Cb通道
                0x02,
                //采样系数：高四位：水平；低四位：垂直
                (1<<4)+1,
                //量化表ID
                0x01,
                //通道ID3_Cr通道
                0x03,
                //采样系数：高四位：水平；低四位：垂直
                (1<<4)+1,
                //量化表ID
                0x01,
        };
        writeArray(SOF0Payload);

        //DHT_霍夫曼表段
        writeMarker(JPEGHeader.DHT);
        //0x00
        bitsDHT=EntropyEncoder.bitsDCluminance;
        valDHT=EntropyEncoder.valDCluminance;
        writeByte((byte) (((2+bitsDHT.length+valDHT.length)>>8)&0xFF));
        writeByte((byte) ((2+bitsDHT.length+valDHT.length)&0xFF));
        for (int i:bitsDHT){
            writeByte((byte) i);
        }
        for (int i:valDHT){
            writeByte((byte) i);
        }
        //DHT_霍夫曼表段
        writeMarker(JPEGHeader.DHT);
        //0x01
        bitsDHT=EntropyEncoder.bitsDCchrominance;
        valDHT=EntropyEncoder.valDCchrominance;
        writeByte((byte) (((2+bitsDHT.length+valDHT.length)>>8)&0xFF));
        writeByte((byte) ((2+bitsDHT.length+valDHT.length)&0xFF));
        for (int i:bitsDHT){
            writeByte((byte) i);
        }
        for (int i:valDHT){
            writeByte((byte) i);
        }

        //DHT_霍夫曼表段
        writeMarker(JPEGHeader.DHT);
        //0x10
        bitsDHT=EntropyEncoder.bitsACluminance;
        valDHT=EntropyEncoder.valACluminance;
        writeByte((byte) (((2+bitsDHT.length+valDHT.length)>>8)&0xFF));
        writeByte((byte) ((2+bitsDHT.length+valDHT.length)&0xFF));
        for (int i:bitsDHT){
            writeByte((byte) i);
        }
        for (int i:valDHT){
            writeByte((byte) i);
        }

        //DHT_霍夫曼表段
        writeMarker(JPEGHeader.DHT);
        //0x11
        bitsDHT=EntropyEncoder.bitsACchrominance;
        valDHT=EntropyEncoder.valACchrominance;
        writeByte((byte) (((2+bitsDHT.length+valDHT.length)>>8)&0xFF));
        writeByte((byte) ((2+bitsDHT.length+valDHT.length)&0xFF));
        for (int i:bitsDHT){
            writeByte((byte) i);
        }
        for (int i:valDHT){
            writeByte((byte) i);
        }

        //Start Of Scan
        writeMarker(JPEGHeader.SOS);
        byte[] SOSPayload={
                //SOS段长度
                0x00,
                0x0C,
                //色彩通道数
                0x03,
                //通道ID1_Y通道
                0x01,
                //Huffman表号
                (0<<4)+0,
                //通道ID2_Cb通道
                0x02,
                //Huffman表号
                (1<<4)+1,
                //通道ID3_Cr通道
                0x03,
                //Huffman表号
                (1<<4)+1,
                //Ss
                0x00,
                //Se
                0x3F,
                //Ah+Al
                0x00,
        };
        writeArray(SOSPayload);
    }
}

class DCT{
    private float[][]       matrix;
    private float[][]       DCTMatrix;
    private int[][]         quantumMatrix;
    private final int       blockLength=8;
    private final double    DCConstant_uv=((double)1/Math.sqrt(blockLength));
    private final double    ACConstant_uv=(Math.sqrt(2)/Math.sqrt(blockLength));
    public  enum            component{luminance, chrominance};

    //JPEG标准提供的灰度/色度量化表
    public static final int[][]   quantum_luminance={
            {16,11,10,16,24,40,51,61},
            {12,12,14,19,26,58,60,55},
            {14,13,16,24,40,57,69,56},
            {14,17,22,29,51,87,80,62},
            {18,22,37,56,68,109,103,77},
            {24,35,55,64,81,104,113,92},
            {49,64,78,87,103,121,120,101},
            {72,92,95,98,112,100,103,99}
    };
    public static final int[][]   quantum_chrominance={
            {17,18,24,47,99,99,99,99},
            {18,21,26,66,99,99,99,99},
            {24,26,56,99,99,99,99,99},
            {47,66,99,99,99,99,99,99},
            {99,99,99,99,99,99,99,99},
            {99,99,99,99,99,99,99,99},
            {99,99,99,99,99,99,99,99},
            {99,99,99,99,99,99,99,99}
    };

    public void initMatrix(float[][] matrix){
        if(matrix.length==blockLength){
            this.matrix=new float[blockLength][blockLength];
            DCTMatrix=new float[blockLength][blockLength];
            quantumMatrix=new int[blockLength][blockLength];
            //手动深拷贝.jpg
            for (int r=0;r<blockLength;r++){
                for (int c=0;c<blockLength;c++){
                    this.matrix[r][c]=matrix[r][c];
                    DCTMatrix[r][c]=matrix[r][c];
                }
            }
            symmetry();
        }
    }

    public void initMatrix(int[][] matrix){
        if(matrix.length==blockLength){
            this.matrix=new float[blockLength][blockLength];
            DCTMatrix=new float[blockLength][blockLength];
            quantumMatrix=new int[blockLength][blockLength];
            //手动深拷贝.jpg
            for (int r=0;r<blockLength;r++){
                for (int c=0;c<blockLength;c++){
                    quantumMatrix[r][c]= matrix[r][c];
                }
            }
        }
    }

    //在通道转换后得到的Y矩阵数据范围在0~255，DCT需要定义域对称，将数据左移128，使其范围落在-128~127
    private void symmetry(){
        for(int r=0;r<blockLength;r++){
            for(int c=0;c<blockLength;c++){
                matrix[r][c]-=128;
            }
        }
    }

    private interface cosCalculator {
        double get(int output,int input);
    }

    //正变换DCT
    float[][] forwardDCT(){
        DCTMatrix=new float[blockLength][blockLength];
        /*
        *被坑了，第一次看的公式多乘了一个1/N，导致数值远小于实际值
        *double constant_1=(double)1/blockLength;
         */
        double constant_2;
        double constant_3;

        cosCalculator cos=(u, x)->{
            double constant_top=u*Math.PI*((2*x)+1);
            double constant_bottom=2*blockLength;

            return Math.cos(constant_top/constant_bottom);
        };

        for(int v=0;v<blockLength;v++){
            for(int u=0;u<blockLength;u++){
                constant_2=(u==0?DCConstant_uv:ACConstant_uv)*(v==0?DCConstant_uv:ACConstant_uv);
                constant_3=0;
                for (int y=0;y<blockLength;y++){
                    for (int x=0;x<blockLength;x++){
                        constant_3+=matrix[y][x]* cos.get(u,x)*cos.get(v,y);
                    }
                }
                DCTMatrix[v][u]= (float) (constant_2*constant_3);
            }
        }
        return DCTMatrix;
    }

    int[][] quantize(component channel_type){
        quantumMatrix=new int[blockLength][blockLength];
        for (int v=0;v<blockLength;v++){
            for(int u=0;u<blockLength;u++){
                if(channel_type==component.luminance){
                    quantumMatrix[v][u]=Math.round(DCTMatrix[v][u]/quantum_luminance[v][u]);
                }else if(channel_type==component.chrominance){
                    quantumMatrix[v][u]=Math.round(DCTMatrix[v][u]/quantum_chrominance[v][u]);
                }
            }
        }
        return quantumMatrix;
    }

    //两个测试输出用的反变换函数
    float[][] reverseDCT(){
        matrix=new float[blockLength][blockLength];
        //double constant_1=(double)2/blockLength;
        double constant;

        cosCalculator cos=(u, x)->{
            double constant_top=u*Math.PI*((2*x)+1);
            double constant_bottom=2*blockLength;

            return Math.cos(constant_top/constant_bottom);
        };

        for(int y=0;y<blockLength;y++){
            for(int x=0;x<blockLength;x++){
                for (int v=0;v<blockLength;v++){
                    for (int u=0;u<blockLength;u++){
                        constant=(u==0?DCConstant_uv:ACConstant_uv)*(v==0?DCConstant_uv:ACConstant_uv);
                        matrix[y][x] += (float) (constant*DCTMatrix[v][u]* cos.get(u,x)*cos.get(v,y));
                    }
                }
            }
        }

        for(int r=0;r<blockLength;r++){
            for(int c=0;c<blockLength;c++){
                matrix[r][c]+=128;
            }
        }

        return matrix;
    }

    float[][] reverseQuantize(component channel_type){
        DCTMatrix=new float[blockLength][blockLength];
        for (int v=0;v<blockLength;v++){
            for(int u=0;u<blockLength;u++){
                if(channel_type==component.luminance){
                    DCTMatrix[v][u]=quantumMatrix[v][u]*quantum_luminance[v][u];
                }else if(channel_type==component.chrominance){
                    DCTMatrix[v][u]=quantumMatrix[v][u]*quantum_chrominance[v][u];
                }
            }
        }
        return DCTMatrix;
    }
}


class EntropyEncoder{
    private BufferedOutputStream output;
    private int[] lastDC={0,0,0};
    private static final int blockLength=8;
    private int[][] matrix;
    private int[] zigzagArray;
    private byte bufByte=0;
    private int  bufIndex=7;
    public enum component{Y, Cb,Cr};

    //MCU处理后只有8x8的矩阵，不再需要对4x4特殊处理
    private static final int[][] zigzagOrder={{0, 0},{0, 1},{1, 0},{2, 0},{1, 1},{0, 2},{0, 3},{1, 2},{2, 1},{3, 0},{4, 0},{3, 1},{2, 2},{1, 3},{0, 4},{0, 5},{1, 4},{2, 3},{3, 2},{4, 1},{5, 0},{6, 0},{5, 1},{4, 2},{3, 3},{2, 4},{1, 5},{0, 6},{0, 7},{1, 6},{2, 5},{3, 4},{4, 3},{5, 2},{6, 1},{7, 0},{7, 1},{6, 2},{5, 3},{4, 4},{3, 5},{2, 6},{1, 7},{2, 7},{3, 6},{4, 5},{5, 4},{6, 3},{7, 2},{7, 3},{6, 4},{5, 5},{4, 6},{3, 7},{4, 7},{5, 6},{6, 5},{7, 4},{7, 5},{6, 6},{5, 7},{6, 7},{7, 6},{7, 7}};
    //private final int[][]   zigzagOrder_luminance={{0, 0},{0, 1},{1, 0},{2, 0},{1, 1},{0, 2},{0, 3},{1, 2},{2, 1},{3, 0},{4, 0},{3, 1},{2, 2},{1, 3},{0, 4},{0, 5},{1, 4},{2, 3},{3, 2},{4, 1},{5, 0},{6, 0},{5, 1},{4, 2},{3, 3},{2, 4},{1, 5},{0, 6},{0, 7},{1, 6},{2, 5},{3, 4},{4, 3},{5, 2},{6, 1},{7, 0},{7, 1},{6, 2},{5, 3},{4, 4},{3, 5},{2, 6},{1, 7},{2, 7},{3, 6},{4, 5},{5, 4},{6, 3},{7, 2},{7, 3},{6, 4},{5, 5},{4, 6},{3, 7},{4, 7},{5, 6},{6, 5},{7, 4},{7, 5},{6, 6},{5, 7},{6, 7},{7, 6},{7, 7}};
    //private final int[][]   zigzagOrder_chrominance={{0, 0},{0, 1},{1, 0},{2, 0},{1, 1},{0, 2},{0, 3},{1, 2},{2, 1},{3, 0},{3, 1},{2, 2},{1, 3},{2, 3},{3, 2},{3, 3}};

    //霍夫曼对照表
    private int[][] DCLuminanceMap;
    private int[][] DCChrominanceMap;
    private int[][] ACLuminanceMap;
    private int[][] ACChrominanceMap;
    //霍夫曼表
    public static final int[] bitsDCluminance = {0x00, 0, 1, 5, 1, 1, 1, 1, 1, 1, 0, 0, 0, 0, 0, 0, 0};
    public static final int[] valDCluminance = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11};
    public static final int[] bitsDCchrominance = {0x01, 0, 3, 1, 1, 1, 1, 1, 1, 1, 1, 1, 0, 0, 0, 0, 0};
    public static final int[] valDCchrominance = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11};
    public static final int[] bitsACluminance = {0x10, 0, 2, 1, 3, 3, 2, 4, 3, 5, 5, 4, 4, 0, 0, 1, 0x7d};
    public static final int[] valACluminance =
            {0x01, 0x02, 0x03, 0x00, 0x04, 0x11, 0x05, 0x12,
                    0x21, 0x31, 0x41, 0x06, 0x13, 0x51, 0x61, 0x07,
                    0x22, 0x71, 0x14, 0x32, 0x81, 0x91, 0xa1, 0x08,
                    0x23, 0x42, 0xb1, 0xc1, 0x15, 0x52, 0xd1, 0xf0,
                    0x24, 0x33, 0x62, 0x72, 0x82, 0x09, 0x0a, 0x16,
                    0x17, 0x18, 0x19, 0x1a, 0x25, 0x26, 0x27, 0x28,
                    0x29, 0x2a, 0x34, 0x35, 0x36, 0x37, 0x38, 0x39,
                    0x3a, 0x43, 0x44, 0x45, 0x46, 0x47, 0x48, 0x49,
                    0x4a, 0x53, 0x54, 0x55, 0x56, 0x57, 0x58, 0x59,
                    0x5a, 0x63, 0x64, 0x65, 0x66, 0x67, 0x68, 0x69,
                    0x6a, 0x73, 0x74, 0x75, 0x76, 0x77, 0x78, 0x79,
                    0x7a, 0x83, 0x84, 0x85, 0x86, 0x87, 0x88, 0x89,
                    0x8a, 0x92, 0x93, 0x94, 0x95, 0x96, 0x97, 0x98,
                    0x99, 0x9a, 0xa2, 0xa3, 0xa4, 0xa5, 0xa6, 0xa7,
                    0xa8, 0xa9, 0xaa, 0xb2, 0xb3, 0xb4, 0xb5, 0xb6,
                    0xb7, 0xb8, 0xb9, 0xba, 0xc2, 0xc3, 0xc4, 0xc5,
                    0xc6, 0xc7, 0xc8, 0xc9, 0xca, 0xd2, 0xd3, 0xd4,
                    0xd5, 0xd6, 0xd7, 0xd8, 0xd9, 0xda, 0xe1, 0xe2,
                    0xe3, 0xe4, 0xe5, 0xe6, 0xe7, 0xe8, 0xe9, 0xea,
                    0xf1, 0xf2, 0xf3, 0xf4, 0xf5, 0xf6, 0xf7, 0xf8,
                    0xf9, 0xfa};
    public static final int[] bitsACchrominance = {0x11, 0, 2, 1, 2, 4, 4, 3, 4, 7, 5, 4, 4, 0, 1, 2, 0x77};

    public static final int[] valACchrominance =
            {0x00, 0x01, 0x02, 0x03, 0x11, 0x04, 0x05, 0x21,
                    0x31, 0x06, 0x12, 0x41, 0x51, 0x07, 0x61, 0x71,
                    0x13, 0x22, 0x32, 0x81, 0x08, 0x14, 0x42, 0x91,
                    0xa1, 0xb1, 0xc1, 0x09, 0x23, 0x33, 0x52, 0xf0,
                    0x15, 0x62, 0x72, 0xd1, 0x0a, 0x16, 0x24, 0x34,
                    0xe1, 0x25, 0xf1, 0x17, 0x18, 0x19, 0x1a, 0x26,
                    0x27, 0x28, 0x29, 0x2a, 0x35, 0x36, 0x37, 0x38,
                    0x39, 0x3a, 0x43, 0x44, 0x45, 0x46, 0x47, 0x48,
                    0x49, 0x4a, 0x53, 0x54, 0x55, 0x56, 0x57, 0x58,
                    0x59, 0x5a, 0x63, 0x64, 0x65, 0x66, 0x67, 0x68,
                    0x69, 0x6a, 0x73, 0x74, 0x75, 0x76, 0x77, 0x78,
                    0x79, 0x7a, 0x82, 0x83, 0x84, 0x85, 0x86, 0x87,
                    0x88, 0x89, 0x8a, 0x92, 0x93, 0x94, 0x95, 0x96,
                    0x97, 0x98, 0x99, 0x9a, 0xa2, 0xa3, 0xa4, 0xa5,
                    0xa6, 0xa7, 0xa8, 0xa9, 0xaa, 0xb2, 0xb3, 0xb4,
                    0xb5, 0xb6, 0xb7, 0xb8, 0xb9, 0xba, 0xc2, 0xc3,
                    0xc4, 0xc5, 0xc6, 0xc7, 0xc8, 0xc9, 0xca, 0xd2,
                    0xd3, 0xd4, 0xd5, 0xd6, 0xd7, 0xd8, 0xd9, 0xda,
                    0xe2, 0xe3, 0xe4, 0xe5, 0xe6, 0xe7, 0xe8, 0xe9,
                    0xea, 0xf2, 0xf3, 0xf4, 0xf5, 0xf6, 0xf7, 0xf8,
                    0xf9, 0xfa};

    public EntropyEncoder(BufferedOutputStream output){
        this.output=output;
        matrix=new int[blockLength][blockLength];
        zigzagArray=new int[blockLength*blockLength];
        initHuf();
    }

    //实在是懒得写范式Huffman构成了，直接搬源码了
    public void initHuf(){
        int[][] DC_matrix0 = new int[12][2];
        int[][] DC_matrix1 = new int[12][2];
        int[][] AC_matrix0 = new int[255][2];
        int[][] AC_matrix1 = new int[255][2];
//        DC_matrix = new int[2][][];
//        AC_matrix = new int[2][][];
        int p, l, i, lastp, si, code;
        int[] huffsize = new int[257];
        int[] huffcode = new int[257];

        /*
         * init of the DC values for the chrominance
         * [][0] is the code   [][1] is the number of bit
         */

        p = 0;
        for (l = 1; l <= 16; l++) {
            for (i = 1; i <= bitsDCchrominance[l]; i++) {
                huffsize[p++] = l;
            }
        }
        huffsize[p] = 0;
        lastp = p;

        code = 0;
        si = huffsize[0];
        p = 0;
        while (huffsize[p] != 0) {
            while (huffsize[p] == si) {
                huffcode[p++] = code;
                code++;
            }
            code <<= 1;
            si++;
        }

        for (p = 0; p < lastp; p++) {
            DC_matrix1[valDCchrominance[p]][0] = huffcode[p];
            DC_matrix1[valDCchrominance[p]][1] = huffsize[p];
        }

        /*
         * Init of the AC hufmann code for the chrominance
         * matrix [][][0] is the code & matrix[][][1] is the number of bit needed
         */

        p = 0;
        for (l = 1; l <= 16; l++) {
            for (i = 1; i <= bitsACchrominance[l]; i++) {
                huffsize[p++] = l;
            }
        }
        huffsize[p] = 0;
        lastp = p;

        code = 0;
        si = huffsize[0];
        p = 0;
        while (huffsize[p] != 0) {
            while (huffsize[p] == si) {
                huffcode[p++] = code;
                code++;
            }
            code <<= 1;
            si++;
        }

        for (p = 0; p < lastp; p++) {
            AC_matrix1[valACchrominance[p]][0] = huffcode[p];
            AC_matrix1[valACchrominance[p]][1] = huffsize[p];
        }

        /*
         * init of the DC values for the luminance
         * [][0] is the code   [][1] is the number of bit
         */
        p = 0;
        for (l = 1; l <= 16; l++) {
            for (i = 1; i <= bitsDCluminance[l]; i++) {
                huffsize[p++] = l;
            }
        }
        huffsize[p] = 0;
        lastp = p;

        code = 0;
        si = huffsize[0];
        p = 0;
        while (huffsize[p] != 0) {
            while (huffsize[p] == si) {
                huffcode[p++] = code;
                code++;
            }
            code <<= 1;
            si++;
        }

        for (p = 0; p < lastp; p++) {
            DC_matrix0[valDCluminance[p]][0] = huffcode[p];
            DC_matrix0[valDCluminance[p]][1] = huffsize[p];
        }

        /*
         * Init of the AC hufmann code for luminance
         * matrix [][][0] is the code & matrix[][][1] is the number of bit
         */

        p = 0;
        for (l = 1; l <= 16; l++) {
            for (i = 1; i <= bitsACluminance[l]; i++) {
                huffsize[p++] = l;
            }
        }
        huffsize[p] = 0;
        lastp = p;

        code = 0;
        si = huffsize[0];
        p = 0;
        while (huffsize[p] != 0) {
            while (huffsize[p] == si) {
                huffcode[p++] = code;
                code++;
            }
            code <<= 1;
            si++;
        }
        for (int q = 0; q < lastp; q++) {
            AC_matrix0[valACluminance[q]][0] = huffcode[q];
            AC_matrix0[valACluminance[q]][1] = huffsize[q];
        }

        DCLuminanceMap=DC_matrix0;
        DCChrominanceMap=DC_matrix1;
        ACLuminanceMap=AC_matrix0;
        ACChrominanceMap=AC_matrix1;
    }

    void initMatrix(int[][] matrix){
        this.matrix=matrix;
        //zigzag扫描
        zigzagArray=zigzagScan(matrix);
    }

    public static int[] zigzagScan(int[][] input){
        int[] output=new int[blockLength*blockLength];
        for (int i=0;i<blockLength*blockLength;i++){
            output[i]=input[zigzagOrder[i][0]][zigzagOrder[i][1]];
        }
        return output;
    }

    interface significantWriter{
        void write(int input,int bits) throws IOException;
    }
    void writeHuffmanBits(component type) throws IOException {
        writeHuffmanBits(type,false);
    }
    void writeHuffmanBits(component type,boolean debug) throws IOException {
        significantWriter writer=(input,bits)->{
            for (int i=bits-1;i>=0;i--){
                writeByte(input&(1<<i),debug);
            }
        };
        int componentID;
        if(type==component.Y)componentID=0;
        else if(type==component.Cb)componentID=1;
        else componentID=2;

        int zeroCnt=0;
        int EOB;
        int  hufIndex;
        byte hufCode;
        int tmp=zigzagArray[0];
        zigzagArray[0]-=lastDC[componentID];
        lastDC[componentID]=tmp;

        //记录下EOB位置
        EOB=0;
        for (int i=zigzagArray.length-1;i>=0;i--){
            if(zigzagArray[i]!=0){
                EOB=i;
                break;
            }
        }

        //Huffman编码部分
        int DCSize=VLI(zigzagArray[0],false);
        if(type==component.Y){
            writer.write(DCLuminanceMap[DCSize][0],DCLuminanceMap[DCSize][1]);
        }else{
            writer.write(DCChrominanceMap[DCSize][0],DCChrominanceMap[DCSize][1]);
        }
        //VLI编码部分
        VLI(zigzagArray[0],true,debug);

        //AC系数编码
         for (int i=1;i<zigzagArray.length;i++){
            if(zigzagArray[i]==0)zeroCnt++;
            //16个前导0的情况
            if(zeroCnt==16){
                //F/0(ZRL标记位)，标记15个前导0加上自身共16个前导0
                if(type==component.Y){
                    writer.write(ACLuminanceMap[0xF0][0],ACLuminanceMap[0xF0][1]);
                }else{
                    writer.write(ACChrominanceMap[0xF0][0],ACChrominanceMap[0xF0][1]);
                }
                zeroCnt=0;
                //这时不需要写入VLI，VLI中0也没有对应的码值
            }
            if(zigzagArray[i]!=0){
                hufCode=0;
                //高四位记录前导零数量
                for (int j=7;j>=4;j--){
                    hufCode=writeByte(hufCode,((zeroCnt&(1<<(j-4)))==0?0:1),j);
                }
                zeroCnt=0;
                //低四位记录VLI位深
                for (int j=3;j>=0;j--){
                    hufCode=writeByte(hufCode,((VLI(zigzagArray[i],false)&(1<<(j)))==0?0:1),j);
                }
                //写入hufCode
                hufIndex=Byte.toUnsignedInt(hufCode);
                if(type==component.Y){
                    writer.write(ACLuminanceMap[hufIndex][0],ACLuminanceMap[hufIndex][1]);
                }else{
                    writer.write(ACChrominanceMap[hufIndex][0],ACChrominanceMap[hufIndex][1]);
                }
                //写入VLICode
                VLI(zigzagArray[i],true,debug);
            }
            //达到EOB
            if(i>=EOB&&EOB<63){
                //写入EOB标记位然后break
                if(type==component.Y){
                    writer.write(ACLuminanceMap[0][0],ACLuminanceMap[0][1]);
                }else{
                    writer.write(ACChrominanceMap[0][0],ACChrominanceMap[0][1]);
                }
                break;
            }
        }
        //flushByte();
    }

    private void writeByte(int bit) throws IOException {
        writeByte(bit,false);
    }
    private void writeByte(int bit,boolean debug) throws IOException {
        if(debug){
            System.out.print(bit==0?0:1);
        }
        if(bit==0){
            bufByte=writeByte(bufByte,0,bufIndex);
            bufIndex--;
        }else{
            bufByte=writeByte(bufByte,1,bufIndex);
            bufIndex--;
        }
        if(bufIndex==-1){
            flushByte(debug);
        }
    }
    private byte writeByte(byte input,int bit,int index) {
        if(bit==0){
            input= (byte) (input&((~1)<<index));
        }else{
            input=(byte) (input|(1<<index));
        }
        return input;
    }
    public void flushByte() throws IOException {
        flushByte(false);
    }
    public void flushByte(boolean debug) throws IOException {
        if(bufIndex<7){
            if(debug){
                System.out.println();
            }
            output.write(bufByte);
            if(bufByte==(byte)0xFF){
                //写入0xFF时再写入0x00防止误读
                output.write(0x00);
            }
            bufByte=0;
            bufIndex=7;
        }
    }


    /** VLI可变长整数编码
     * @return 返回VLI编码的位长
     * @param num 被编码数
     * @param write 是否写入output流
     */
    public int VLI(int num,boolean write) throws IOException {
        return VLI(num,write,false);
    }
    public int VLI(int num,boolean write,boolean debug) throws IOException {
        int size=0;
        if(num>0){
            for(int i=31;i>=0;i--){
                if((num&(1<<i))!=0)break;
                size++;
            }
            size=32-size;
            if(write){
                for (int i=size-1;i>=0;i--){
                    writeByte(num&(1<<i),debug);
                }
            }
        }else{
            num=-num;
            for(int i=31;i>=0;i--){
                if((num&(1<<i))!=0)break;
                size++;
            }
            size=32-size;
            if(write){
                num=~(num);
                for (int i=size-1;i>=0;i--){
                    writeByte(num&(1<<i),debug);
                }
            }
        }
        return size;
    }
}