# JPEG编码过程详解
关于本人JPEG编码器的项目已经上传至Github：https://github.com/Smileslime47/JPEGCompressor

本文旨在对JPEG编码过程中的细节步骤进行说明，具体原理部分请参照Wikipedia

部分图片素材源自：https://en.wikipedia.org/wiki/JPEG
## 色彩空间转换
原始图像的像素点是以RGB形式存储的，即每个像素点由3个字节的数据组成，分别为Red通道、Green通道和Blue通道三个色度通道。JPEG很大一部分压缩算法是基于人眼对亮度和色度敏感性的差异而实现的，所以我们需要将RGB通道转换为一种叫做YCbCr的通道，即每个像素点由3个字节数据组成，分别为Y通道（亮度通道）、Cb通道（Blue）和Cr通道（Red），从而实现亮度数据的分离

这两个色彩通道的转换公式网上已经有很多了，这里再给一下：
### RGB到YCbCr
- $Y = 0.299 \times R + 0.587 \times G + 0.114 \times B$
- $C_B = (-0.16874 \times R - 0.33126 \times G + 0.5 \times B) + 128$
- $C_R = (0.5 \times R - 0.41869 \times G - 0.08131 \times B) + 128$
### YCbCr到RGB
- $R=Y + 1.402 \times (C_R - 128)$
- $G=Y - 0.34414 \times (C_B - 128) - 0.71414 \times (C_R - 128)$
- $B=Y + 1.772 \times (C_B - 128)$

## 色度抽样
在将RGB转换为YUV空间后，一个像素的大小并没有变化，原来是由3个字节分别存储R、G、B三个色度通道的信息，而现在变成了一个像素由3个字节分别存储Y、Cb、Cr一个亮度通道和两个色度通道的信息，对于4个像素，我们的数据量仍然为12个字节

由于人眼对于亮度变化的感知能力要远强于对色度变化的感知能力，我们可以在保留亮度数据的情况下适当地删减一些色度数据，或者说，原来我们对全部色度数据采样的话，那么我们采用一种每J个像素点只采样其中a个色度数据的方式

对于色度抽样的系数表示，我们常用的命名法是：J:a:b命名法
- J:水平像素宽度的参照，通常取4
- a:第一行J区域中的Cb、Cr样本数
- b:第二行J区域中的Cb、Cr样本数与a的变化量（大部分情况下b=a或b=0）

||||||||
|--|--|--|--|--|--|--|
|Y1|U1|V1|-|Y2|U2|V2|
|Y3|U3|V3|-|Y4|U4|V4|

这种采样方式我们叫做4:4:4采样，即每4个**水平**像素点Cb、Cr取样4次，且每行之间无变化

||||||||
|--|--|--|--|--|--|--|
|Y1|U1|V1|-|Y2|U1|V1|
|Y3|U3|V3|-|Y4|U3|V3|

这种采样方式我们叫做4:2:2采样，即每4个**水平**像素点Cb、Cr取样2次，且每行之间无变化

||||||||
|--|--|--|--|--|--|--|
|Y1|U1|V3|-|Y2|U1|V3|
|Y3|U1|V3|-|Y4|U1|V3|

这种采样方式我们叫做4:2:0采样，即每4个**水平**像素点Cb、Cr取样2次，而b=0的含义为：**当Cb在一行取样两次时，在另一行则不取样；反之当Cr在一行取样两次时，在另一行则不取样**

在水平4像素内取样两次，即在水平2像素内取样一次，看这个2x2的区域可以发现，第一行取样了一次U1后，第二行是不取样任何U值的（直接用U1的值），第二行取样了一次V3后，第一行也是不取样任何V值的（直接用V3的值），这种Cb和Cr交错取样的方式就是我们常说的4:2:0采样

JPEG标准常用的是**奇数行采用Cb数据（1、3、5、...）、偶数行采样Cr数据（2、4、6、...），且只采样偶数列的数据**，如上述表格所示（均以1起始而非0起始）

当我们采样4:2:0采样时，每个2x2的像素块内4个Y值仅需要1个Cb值和Cr值共六组数据，原先4个像素需要12个字节存储，在通过4:2:0采样后只需要6个字节就可以完成存储。在采用4:2:0的色度抽样后，数据量被压缩至原来的50%，我们完成了第一次**有损压缩**。

JPEG一般常用4:2:0的色度抽样，但并不代表其他的采样系数不会被考虑，在100%的压缩质量下，JPEG甚至会采用4:4:4，即不进行色度抽样来保证图像信息的完整性

## 分块
这里需要引入一个**最小编码单元**（MCU）的概念。即每对于一个$r \times j$大小的像素块，我们以这个大小的像素块为一个编码单位即MCU，每次写入比特流时将**这个MCU的全部数据独立编码好一次性推入比特流**，然后处理下一个MCU的数据再推入比特流

对于JPEG标准来说，后续处理步骤都是以$8\times8$的矩阵为单位进行处理的，但这并不代表MCU的大小就是$8\times8$，考虑一个问题：在JPEG中，**Y、Cb、Cr三组通道的数据是分别存储的**，当MCU大小取$8\times8$时，我们对一个$8\times8$的像素区域进行色彩空间转换得到YCbCr矩阵，然后对Cb和Cr进行色度抽样后会得到两个$4\times4$的矩阵，这明显不符合我们后续处理的要求。所以在4:2:0采样中MCU的大小实际上是$16\times16$的，这样在色度抽样后Cb和Cr矩阵各自由$16\times16$缩减为$8\times8$的大小，而原来$16\times16$的Y矩阵我们则按照：**左上、右上、左下、右下**的顺序将其分割为四个$8\times8$大小的矩阵

至此，我们得到了一个MCU的六个数据区块（block）：Y1、Y2、Y3、Y4、Cb、Cr

在后续阶段我们对这六个数据矩阵分别处理然后按顺序推入比特流即可，即比特流内部的编码顺序为：

$$MCU_1(Y_1,Y_2,Y_3,Y_4,C_B,C_R),MCU_2(Y_1,Y_2,Y_3,Y_4,C_B,C_R),\dots$$

要注意的是，当图像的宽度和高度不满足16的倍数时，我们需要将图像补全至16的倍数，而为了防止振铃效应（由于色度抽样和后续DCT的问题，一个$8\times8$区块内的色度数据会互相影响，如果无脑写入0,0,0或者128,128,128这类数据会导致边缘处理不自然），补全部分的像素数据一般为**边缘像素的延伸**

```Java
//对于图像尺寸不满足16的倍数的，补全到16的倍数，方便后面进行分块
completionWidth = ((imageWidth % MCULength != 0) ? (int) (Math.floor((double) imageWidth / MCULength) + 1) * MCULength : imageWidth);
completionHeight = ((imageHeight % MCULength != 0) ? (int) (Math.floor((double) imageHeight / MCULength) + 1) * MCULength : imageHeight);
```

在结合分块思想和色度抽样的基本思想后，我们得到了前三步的代码：
```Java
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
    }
}
```

## 离散余弦变换
![](https://wikimedia.org/api/rest_v1/media/math/render/svg/eed8c00e62db6618fd452d3905a03f842c30ce34)

此时我们得到的YCbCr矩阵数据范围落在[0,255]，为了让后续DCT步骤的动态范围缩小，我们需要让数据范围变为以0为中心的[-128,127]，这样在DCT处理后数据才能尽可能以0为中心，在后续量化过程中获得更多的0，所以对于YUV矩阵我们需要将所有元素减去128来**归一化**
```Java
private void symmetry(){
    for(int r=0;r<blockLength;r++){
        for(int c=0;c<blockLength;c++){
            matrix[r][c]-=128;
        }
    }
}
```
在归一化后我们得到了：

![](https://wikimedia.org/api/rest_v1/media/math/render/svg/f69a5e277c8e5d58ea12abdf1b102668b9bb5bf1)

**离散余弦变换**是将图像数据作为一种信号流来处理，对其进行类似于**快速傅里叶变换**的操作，将图像数据转换为若干个不同频率的余弦波的线性组合。在区块大小为$8\times8$的情况下，我们将其转换为64个余弦波的线性组合（其中包含一个频率为0的**直流分量**）

维基上已经直接给出了当**N取8**时的DCT正变换公式：

$$G_{u,v}=\frac{1}{4}α(u)α(v)\sum^7_{x=0}\sum^7_{y=0}g_{x,y}cos[\frac{(2x+1)u\pi}{16}]cos[\frac{(2y+1)v\pi}{16}]$$
- u为水平空间频率，范围为$0\leq u<8$，实际上对应的就是DCT矩阵中的横坐标
- v为垂直空间频率，范围为$0\leq u<8$，实际上对应的就是DCT矩阵中的纵坐标
- $g_{x,y}$为坐标$(x,y)$处的数据值
- $G_{u,v}$为坐标$(u,v)$处的DCT系数


$$α(u)=\begin{cases}\frac{1}{\sqrt{2}}, & \text{if}\ u=0 \cr 1, & \text{otherwise}\end{cases}$$

是一个使变换[正交](https://en.wikipedia.org/wiki/Orthonormality)的正规化系数


这里我给出一个通用性更强的DCT-II公式，其中N为矩阵大小，自然地，我们取8：

$$G_{u,v}=α(u)α(v)\sum^7_{x=0}\sum^7_{y=0}g_{x,y}cos[\frac{(2x+1)u\pi}{2N}]cos[\frac{(2y+1)v\pi}{2N}]$$

$$α(u)=\begin{cases}\frac{1}{\sqrt{N}}, & \text{if}\ u=0 \cr \frac{\sqrt{2}}{\sqrt{N}}, & \text{otherwise}\end{cases}$$

此外还有一个反变换的DCT-III公式，在解码时可以将DCT矩阵转换回YUV矩阵：

$$g_{x,y}=\sum^7_{x=0}\sum^7_{y=0}α(u)α(v)G_{u,v}cos[\frac{(2x+1)u\pi}{2N}]cos[\frac{(2y+1)v\pi}{2N}]$$

$$α(u)=\begin{cases}\frac{1}{\sqrt{N}}, & \text{if}\ u=0 \cr \frac{\sqrt{2}}{\sqrt{N}}, & \text{otherwise}\end{cases}$$

在经过DCT处理后就可以得到DCT矩阵：
![](https://wikimedia.org/api/rest_v1/media/math/render/svg/46ee57df2a309dd59e0a10c9ab83e8b86d712e3e)

要注意，当u和v同时取0，即DCT矩阵左上角的那个数值，其频率为0，我们称之为**DC系数或直流系数**，而剩下的63个值则叫做**AC系数或交流系数**，观察可以发现DC系数远大于AC系数

```Java
    private final double    DCConstant_uv=((double)1/Math.sqrt(blockLength));
    private final double    ACConstant_uv=(Math.sqrt(2)/Math.sqrt(blockLength));
    float[][] forwardDCT(){
    DCTMatrix=new float[blockLength][blockLength];
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
```

同理，我们在解码步骤可以写出反变换的公式，这里不再赘述

## 量化
量化和色度抽样是JPEG中**唯二的有损压缩**，要注意DCT矩阵和原始数据矩阵中的坐标关系没有对应关系，即DCT[0,0]和matrix[0,0]没有对应关系
- DCT[0,0]指的是整个$8\times8$区块中**水平频率分量u和垂直频率分量v均为0的余弦波在这个区块中的权重**
- matrix[0,0]指的则是**这个区块中坐标位于[0,0]的像素点在这个色彩通道上的取值**

量化是基于人眼对图像中高频数据的敏感程度小于低频数据的原理，将DCT后得到的频谱矩阵中的高频分量删除来压缩数据。实际操作也很简单，给定一张量化表quantum，我们只需要将DCT矩阵中对应坐标的数据除以量化表中对应坐标的数据然后四舍五入取整即可，即：

```Java
quantumMatrix[v][u]=Math.round(DCTMatrix[v][u]/quantum[v][u]);
```

JPEG标准中已经给出了在50%压缩质量下对于亮度和色度的量化表，在不同的压缩质量下量化表也会有所不同，在100%压缩质量下通常量化表为一张**全1矩阵**，即不进行量化操作

亮度量化表
|||||||||
|--|--|--|--|--|--|--|--|
|16|11|10|16|24|40|51|61|
|12|12|14|19|26|58|60|55|
|14|13|16|24|40|57|69|56|
|14|17|22|29|51|87|80|62|
|18|22|37|56|68|109|103|77|
|24|35|55|64|81|104|113|92|
|49|64|78|87|103|121|120|101|
|72|92|95|98|112|100|103|99|

色度量化表
|||||||||
|--|--|--|--|--|--|--|--|
|17|18|24|47|99|99|99|99|
|18|21|26|66|99|99|99|99|
|24|26|56|99|99|99|99|99|
|47|66|99|99|99|99|99|99|
|99|99|99|99|99|99|99|99|
|99|99|99|99|99|99|99|99|
|99|99|99|99|99|99|99|99|
|99|99|99|99|99|99|99|99|

在上述DCT矩阵经过量化处理后我们就有了如下量化矩阵：

![](https://wikimedia.org/api/rest_v1/media/math/render/svg/dfedb02fc67c95d021b46c13f4fb21c55a361671)

## 熵编码
熵编码其实是JPEG压缩中最麻烦的一步，也很难讲清楚，它分为多个步骤

### Zigzag扫描

![](https://upload.wikimedia.org/wikipedia/commons/4/43/JPEG_ZigZag.svg)

在得到了量化矩阵后，我们按照上图顺序读取该矩阵的值，并根据读取顺序将其存入一个64长度的**一维数组**中，由于量化的过程中右下角的高频分量被大量取整为0，在进行Zigzag扫描后可以让一维数组中的**有效值**集中在前方，而大量的0集中在数组后方

这里直接给出Zigzag数组和二维矩阵的坐标对应关系：
```Java
private static final int[][] zigzagOrder={{0, 0},{0, 1},{1, 0},{2, 0},{1, 1},{0, 2},{0, 3},{1, 2},{2, 1},{3, 0},{4, 0},{3, 1},{2, 2},{1, 3},{0, 4},{0, 5},{1, 4},{2, 3},{3, 2},{4, 1},{5, 0},{6, 0},{5, 1},{4, 2},{3, 3},{2, 4},{1, 5},{0, 6},{0, 7},{1, 6},{2, 5},{3, 4},{4, 3},{5, 2},{6, 1},{7, 0},{7, 1},{6, 2},{5, 3},{4, 4},{3, 5},{2, 6},{1, 7},{2, 7},{3, 6},{4, 5},{5, 4},{6, 3},{7, 2},{7, 3},{6, 4},{5, 5},{4, 6},{3, 7},{4, 7},{5, 6},{6, 5},{7, 4},{7, 5},{6, 6},{5, 7},{6, 7},{7, 6},{7, 7}};
```

### VLI编码
在具体讲述熵编码的步骤前，要先介绍一下VLI编码和范式霍夫曼编码的算法：

可变长整数编码是一种用不同的位数来表示数字的编码方式，这种编码的特点在于：**绝对值越小的数字占用的位数越少**

|实际值|位深|二进制表示|
|:--:|:--:|:--:|
|0|0|不表示|
|-1,1|1|0,1|
|-3,-2,2,3|2|00,01,10,11|
|-7,-6,-5,-4,4,5,6,7|3|000,001,010,011,100,101,110,111|
|-15, …, -8, 8, …, 15|4|0000, …, 0111, 1000, …, 1111|
|-31, …, -16, 16, …, 31|5|0 0000, …, 0 1111, 1 0000, …, 1 1111|
|-63, …, -32, 32, …, 63|6|00 0000, …, …, 11 1111|
|-127, …, -64, 64, …, 127|7|000 0000, …, …, 111 1111|
|-255, …, -128, 128, …, 255|8|0000 0000, …, …, 1111 1111|
|-511, …, -256, 256, …, 511|9|0 0000 0000, …, …, 1 1111 1111|
|-1023, …, -512, 512, …, 1023|10|00 0000 0000, …, …, 11 1111 1111|
|-2047, …, -1024, 1024, …, 2047|11|000 0000 0000, …, …, 111 1111 1111|
|-4095, …, -2048, 2048, …, 4095|12|0000 0000 0000, …, …, 1111 1111 1111|
|-8191, …, -4096, 4096, …, 8191|13|0 0000 0000 0000, …, …, 1 1111 1111 1111|
|-16383, …, -8192, 8192, …, 16383|14|00 0000 0000 0000, …, …, 11 1111 1111 1111|
|-32767, …, -16348, 16348, …, 32767|15|000 0000 0000 0000, …, …, 111 1111 1111 1111|

由于这种编码方式的位深是**不定的**，在读取VLI编码前必须要**先指定读取的位数**，当指定读取的位数为0时，则说明数字为0，不需要占用位数

虽然这张表格乍一眼看上去很迷惑，但是实际上规律很简单：
- 对于正数而言，其二进制表示就是它本身
- 对于负数而言，其二进制表示是其正数的二进制表示按位取反（如-7（000）就是7（111）的按位取反）

于是我们可以很轻松地写出VLI的代码：
```JAVA
public int VLI(int num,boolean write) throws IOException {
    int size=0;
    if(num>0){
        for(int i=31;i>=0;i--){
            if((num&(1<<i))!=0)break;
            size++;
        }
        size=32-size;
        if(write){
            for (int i=size-1;i>=0;i--){
                writeByte(num&(1<<i));
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
                writeByte(num&(1<<i));
            }
        }
    }
    //返回VLI的位数
    return size;
}
```

### 范式霍夫曼编码
范式霍夫曼编码是霍夫曼编码的一种变体，它旨在制定一个统一的标准，使我们可以通过少量的数据（位表和值表）快速构建出一颗霍夫曼树的对照表

关于范式霍夫曼编码的具体内容可以参照：[这篇文章](https://zhuanlan.zhihu.com/p/72044095)，本文不再赘述，只说明如何通过给定的霍夫曼表构建对照数组

此外，你也可以针对图像自行优化构建霍夫曼树。

JPEG标准给定了四张推荐的霍夫曼表（DC亮度、DC色度、AC亮度、AC色度），每张表由两个数组构成：**位表**和**值表**，由于AC系数的霍夫曼表比较大，这里先拿DC系数的霍夫曼表举例
```Java
public static final int[] bitsDCluminance = {0x00, 0, 1, 5, 1, 1, 1, 1, 1, 1, 0, 0, 0, 0, 0, 0, 0};
public static final int[] valDCluminance = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11};

public static final int[] bitsDCchrominance = {0x01, 0, 3, 1, 1, 1, 1, 1, 1, 1, 1, 1, 0, 0, 0, 0, 0};
public static final int[] valDCchrominance = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11};    
```

拿DC亮度霍夫曼表举例，位表中第i个元素代表**位深为i的码最多可以映射几个值**，而我们需要做的就是将值表中的数据逐个往里填充，并遵循以下规则
- 对照表中的第一个数字必然全为0
- 位深相同的情况下，每个值对应的二进制表示为上一个值的二进制表示+1
- 位深+1的情况下，第一个值对应的二进制表示为上一个值的二进制表示+1的二倍

举例来说，看位表，我们可以得知：
- 1bit可以映射0个值
- 2bit可以映射1个值
- 3bit可以映射5个值
- ...

然后我们逐个往里填充：
- 填充0：2bit，有0->00
- 填充1：3bit，有1->010
- 填充2：3bit，有2->011
- 填充3：3bit，有3->100
- 填充4：3bit，有4->101
- 填充5：3bit，有5->110
- 填充6：4bit，有6->1110    
- 填充7：5bit，有7->11110  
- ...

这样一来，我们就可以构建出**值表中每一个值对应的霍夫曼码**了

### 熵编码步骤
对于得到的Zigzag一维数组中每个**非零值**，我们都需要由两部分来表示：

|部分1|部分2|
|--|--|
|((前导0数量),VLI编码长度)|VLI编码|

其中VLI编码已经是压缩的比特流了，而部分1则需要通过对照上述的霍夫曼表来找到对应的二进制表示

我们先对**DC系数编码**

DC系数有两个特殊现象：
- DC系数本身数值较大
- 相同通道的相邻DC系数差距较小

所以我们对DC系数采用**差分编码**，每个DC系数的存储值被修改为该DC系数的实际值与上一个区块DC系数的实际值的差值。要注意的是，这个差分编码并不是面向区块的，而是面向通道的，也就是说每个色彩通道（Y、Cb、Cr）要独立计算。
```Java
if(type==component.Y)componentID=0;
else if(type==component.Cb)componentID=1;
else componentID=2;

int tmp=zigzagArray[0];
zigzagArray[0]-=lastDC[componentID];
lastDC[componentID]=tmp;
```

由于DC系数没有前导0，部分1就是VLI编码的位深，我们记录下此时DC系数对应的VLI编码的位深，然后在霍夫曼表中找到对应的二进制表示，推入比特流，然后再将DC系数对应的VLI编码推入比特流即可

```Java
//Huffman编码部分
int DCSize=VLI(zigzagArray[0],false);
if(type==component.Y){
    writer.write(DCLuminanceMap[DCSize][0],DCLuminanceMap[DCSize][1]);
}else{
    writer.write(DCChrominanceMap[DCSize][0],DCChrominanceMap[DCSize][1]);
}
//VLI编码部分
VLI(zigzagArray[0],true,debug); 
```

对于**AC系数**的编码：

当我们遇到0时需要跳过并让零计数器+1，遇到非零值时开始编码：

其中部分1的**高四位**是这个非零值前0值的数量，**低四位**是这个非零值VLI编码的位深
- 低四位：在上面的VLI对照表中可以看出，15位已经足够表示到32767，而到了这一步通常是不会出现这么大的数据的
- 高四位：问题出在高四位这里，高四位仍然采用的是朴素的二进制编码，即0~F最多只能表示该非零值前有15个0

当零计数器计数到16个0时，我们采用一个特殊的标识符ZRL:**0xF0**即240来表示这里有16个连续0（0xF0中——F：这个数字前有15个0，0：这个数字本身也是一个0，实际上是在告诉解码器这里有16个0），然后零计数器归零。要注意的是这里的240并非比特流的实际码，我们仍然需要在霍夫曼表中找到240对应的二进制编码再推入比特流

当从**数组中间**的某一个位置开始到数组末尾所有的元素均为0时，我们同样需要一个特殊的标识符EOB:**0x00**，告诉解码器从这个位置开始到block的最后一个元素均为0。同样地，EOB对应的二进制编码需要在霍夫曼表中查找。

要注意的是：
- 在这个区块编码推入比特流后，不需要flushByte，直接在后面继续写下一个区块的比特流即可，除非已经是图像的最后一个区块了
- **当一维数组的最后一位不为0时，不需要写EOB**，否则会导致解码器出错
- 当写入的Byte为0XFF时，一定要在后面**再插入一个0x00来转译**，否则解码器会将其当作JPEG的标志段处理

于是我们有代码：

```Java
    void writeHuffmanBits(component type) throws IOException {
        significantWriter writer=(input,bits)->{
            for (int i=bits-1;i>=0;i--){
                writeByte(input&(1<<i));
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
            //达到EOB（若EOB为63则不写入EOB）
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
    }
```

## 文件头
上面就是编码JPEG的全部步骤了，但是在写入熵编码数据前我们需要先写好JPEG的文件头

JPEG标准规定0xFF是一个标志段的起始标志，其下一个Byte说明了这个标志段的类型

许多标志段后面会负载（payload）许多元数据（如DHT段记录了霍夫曼表，DQT段记录了量化表等），这时在标志段类型后面还会再跟随2Byte记录下该标志段的**字节长度**

要注意的是，当用多字节表示数据时，字节高位代表的是数字中的高位数据

### SOI
**0xFFD8**：Start Of Image，所有JPEG图片都以SOI起始

### APP0
APP0段用于指示这是一张JFIF标准的JPEG图片
|含义|Byte长度|该项目默认Byte值|解释|
|--|--|--|--|
|APP0|2|0xFF E0|表示JFIF标志段起始|
|长度|2|0x00 10|该标志段长度，通常为16|
|"JFIF"|5|0x4A 46 49 46 00|"JFIF"的ASCII表示|
|版本号|2|0x01 01|通常在01.00-01.02之间|
|坐标单位|1|0x00|0：无单位，1：英寸，2：厘米|
|水平+垂直分辨率|4|0x00 01 00 01|仅当坐标单位不为0时有意义|
|缩略图分辨率|2|0x00 00||

### DQT
DQT段用于存储量化表并设置对应ID
|含义|Byte长度|该项目默认Byte值|解释|
|--|--|--|--|
|DQT|2|0xFF DB|表示DQT段起始|
|长度|2|0x00 43|该标志段长度，通常为0x43|
|精度|1|0x0X（X=0~3）|**高四位**：存储精度：0为1byte存储一个值，1为2byte存储一个值<br>**低四位**：量化表ID，通常0为亮度量化表，1为色度量化表|
|量化表数据|64||量化表数据，要注意的是，这里存储的顺序仍然是以**Zigzag扫描顺序**存储的|

你可以在一个DQT段存储多个量化表，每个量化表包含精度ID值和数据段，你也可以在多个DQT段各存储一张量化表（更常见）

### SOF
SOF段存储了关于图片解析的许多重要的元数据
|含义|Byte长度|该项目默认Byte值|解释|
|--|--|--|--|
|SOF0/SOF2|2|0xFF C0/C2|C0表示这是基线式JPEG，C2表示这是渐进式JPEG，这里选择0xFFC0|
|长度|2|0x00 11|该标志段长度，通常为17（8+3x3）|
|精度|1|0x08|图片位深，通常为8bit|
|图片高度|2|(imageHeight>>8)&0xFF<br>(imageHeight)&0xFF||
|图片宽度|2|(imageWidth>>8)&0xFF<br>(imageWidth)&0xFF||
|色彩通道数|1|0x03|通常为3|
|通道ID|1|0x01~03|ID：1->Y通道，2->Cb通道，3->Cr通道|
|采样系数|1|(2<<4)+2或(1<<4)+1|在4:2:0下，Y为(2<<4)+2，其他为(1<<4)+1|
|量化表ID|1|0x00~03|通常Y通道为0x00，其他通道为0x01|
|...|...|...|...|

要注意的是，最后三个选项是一组通道的数据，三组通道要各写一遍，所以标志段长度为8+3x通道数

### DHT
DHT段存储了关于霍夫曼位表和值表的数据
|含义|Byte长度|该项目默认Byte值|解释|
|--|--|--|--|
|DHT|2|0xFF C4|表示DHT段起始|
|长度|2|(2+bitsDHT.length+valDHT.length)>>8)&0xFF<br>(2+bitsDHT.length+valDHT.length)&0xFF|该标志段长度，通常为位表和值表的长度之和|
|表ID|1|0xXY|X：0表示DC表，1表示AC表<br>Y：表ID，一般亮度表为0，色度表为1|
|位表数据|16||通常为16byte长|
|值表数据|不定长||不定长|

和DQT段相同，可以在一个DHT段存储多个霍夫曼表，不过在多个DHT段各存储一张表更常见

但是霍夫曼表直接将数据推入比特流即可，不需要Zigzag扫描

### SOS
SOS表示扫描开始，在读取完SOS段后紧接着就是熵编码的比特流了
|含义|Byte长度|该项目默认Byte值|解释|
|--|--|--|--|
|SOS|2|0xFF DA|表示SOS段起始|
|长度|2|0x00 0C|该标志段长度，通常为12|
|色彩通道数|1|0x03|通常为3|
|通道ID|1|0x01~03|ID：1->Y通道，2->Cb通道，3->Cr通道|
|霍夫曼表ID|1|0xXY|高四位：DC系数的表ID<br>低四位：AC系数的表ID<br>通常Y通道为0x00，其他通道为0x11|
|...|...|...|...|
|Ss|1|0x00||
|Se|1|0x3F||
|Ah+Al|1|0x00||

和SOF段相同，通道ID+霍夫曼表ID是一组通道的数据，三个通道要各写一遍

在写完SOS段后就可以推入熵编码的比特流了

### EOI
**0xFFD9**：标志着图片数据的末尾
