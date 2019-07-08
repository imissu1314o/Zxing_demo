/*
 * Copyright 2007 ZXing authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.zxing.qrcode;

import android.content.Context;
import android.graphics.Rect;
import android.hardware.Camera;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Build;
import android.support.annotation.RequiresApi;
import android.util.Log;
import android.widget.Toast;

import com.common.zxing.CaptureActivity;
import com.common.zxing.camera.CameraManager;
import com.common.zxing.light.LightSensorManager;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.ChecksumException;
import com.google.zxing.DecodeHintType;
import com.google.zxing.FormatException;
import com.google.zxing.NotFoundException;
import com.google.zxing.Reader;
import com.google.zxing.Result;
import com.google.zxing.ResultMetadataType;
import com.google.zxing.ResultPoint;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.common.DecoderResult;
import com.google.zxing.common.DetectorResult;
import com.google.zxing.qrcode.decoder.Decoder;
import com.google.zxing.qrcode.decoder.QRCodeDecoderMetaData;
import com.google.zxing.qrcode.detector.Detector;

import java.util.List;
import java.util.Map;

/**
 * This implementation can detect and decode QR Codes in an image.
 *
 * @author Sean Owen
 */
public class QRCodeReader implements Reader {


  private LightSensorManager lightSensorManager; //光线传感器类
  private SensorManager sensorManager;  //光线传感器
  private int scanNum=0;//控制同一放大程度的检测次数

  //第三步：对传感器信号进行监听
  private SensorEventListener listener = new SensorEventListener() {
    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR1)
    @Override
    public void onSensorChanged(SensorEvent event) {
      float lux = event.values[0];//获取光线强度
      //提示当前光照强度
//            Toast.makeText(activity,
//                    "当前光照强度：" + event.values[0] + "勒克斯", Toast.LENGTH_SHORT).show();

      CameraManager cameraManager=activity.getCameraManager();
      Camera camera = cameraManager.getCamera();
      Camera.Parameters parameters = camera.getParameters();
//      Log.d( "最小",String.valueOf( parameters.getMinExposureCompensation() ) );
//      Log.d( "最大",String.valueOf( parameters.getMaxExposureCompensation() ) );
//      Log.d( "当前光照强度",String.valueOf( lux ) );
//      Log.d( "曝光度",String.valueOf( parameters.getExposureCompensation() ) );
      if(lux<50){
        parameters.setExposureCompensation( 3 );
      }else if(lux>=50&&lux<=100){
        parameters.setExposureCompensation( 2 );
      }else if(lux>100&&lux<200){
        parameters.setExposureCompensation( 1 );
      }else if(lux>=200&&lux<=400){
        parameters.setExposureCompensation( 0 );
      }else if(lux>400){
        parameters.setExposureCompensation( -1 );
      }else if(lux>500){
        parameters.setExposureCompensation( -2 );
      }
//      parameters.setExposureCompensation( 4 );
      camera.setParameters( parameters );
      if(activity == null || activity.isDestroyed() || activity.isFinishing()){
          sensorManager.unregisterListener( listener );
      }


    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }
  };

  private static final ResultPoint[] NO_POINTS = new ResultPoint[0];

  private final Decoder decoder = new Decoder();

  protected final Decoder getDecoder() {
    return decoder;
  }

  private CaptureActivity activity;

  public QRCodeReader(CaptureActivity activity) {
    this.activity = activity;
//    //第一步：获取 SensorManager 的实例
//    sensorManager = (SensorManager) activity.getSystemService( Context.SENSOR_SERVICE);
//    //第二步：获取 Sensor 传感器类型
//    Sensor sensor = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT);
//    //第四步：注册 SensorEventListener
//    sensorManager.registerListener(listener,sensor,SensorManager.SENSOR_DELAY_UI);
  }

  /**
   * Locates and decodes a QR code in an image.
   *
   * @return a String representing the content encoded by the QR code
   * @throws NotFoundException if a QR code cannot be found
   * @throws FormatException if a QR code cannot be decoded
   * @throws ChecksumException if error correction fails
   */
  @Override
  public Result decode(BinaryBitmap image) throws NotFoundException, ChecksumException, FormatException {
    return decode(image, null);
  }

  @Override
  public final Result decode(BinaryBitmap image, Map<DecodeHintType,?> hints)
      throws NotFoundException, ChecksumException, FormatException {


    DecoderResult decoderResult;
    ResultPoint[] points;
    if (hints != null && hints.containsKey(DecodeHintType.PURE_BARCODE)) {
      BitMatrix bits = extractPureBits(image.getBlackMatrix());
      decoder.setActivity( activity );
      decoderResult = decoder.decode(bits, hints);

      points = NO_POINTS;
//      Toast.makeText( activity,"pure",Toast.LENGTH_SHORT ).show();
    } else {

      if(scanNum<=10){
        scanNum++;
      }
      //1、将图像进行二值化处理，1、0代表黑、白。( 二维码的使用getBlackMatrix方法 )
      //2、寻找定位符、校正符，然后将原图像中符号码部分取出。（detector代码实现的功能）
      DetectorResult detectorResult = new Detector(image.getBlackMatrix(),activity).detect(hints);
//      Toast.makeText( activity,"not pure",Toast.LENGTH_SHORT ).show();
//      if(detectorResult==null){
//        Log.d( "detect","检测失败" );
//        Toast.makeText( activity,"检测失败",Toast.LENGTH_LONG ).show();
//      }else{
//        Log.d( "detect","检测成功" );
//        Toast.makeText( activity,"检测成功",Toast.LENGTH_LONG ).show();
//      }

      if(scanNum>10){  //控制每个放大程度上的检测次数
        doCameraZoom( detectorResult,image.getBlackMatrix() );
        scanNum=0;
      }

      //3、对符号码矩阵按照编码规范进行解码，得到实际信息（decoder代码实现的功能）
      decoderResult = decoder.decode(detectorResult.getBits(), hints);
      points = detectorResult.getPoints();
//      Toast.makeText(activity,points[0].getX()+" "+points[0].getY()+" "+points[1].getX()+" "+points[1].getY()+" "+points[2].getX()+" "+points[2].getY(),Toast.LENGTH_SHORT).show();

    }

    // If the code was mirrored: swap the bottom-left and the top-right points. 如果码是镜像的：交换左下角和右上角。
      if (decoderResult.getOther() instanceof QRCodeDecoderMetaData) {
      ((QRCodeDecoderMetaData) decoderResult.getOther()).applyMirroredCorrection(points);
    }

    Result result = new Result(decoderResult.getText(), decoderResult.getRawBytes(), points, BarcodeFormat.QR_CODE);
    List<byte[]> byteSegments = decoderResult.getByteSegments();
    if (byteSegments != null) {
      result.putMetadata(ResultMetadataType.BYTE_SEGMENTS, byteSegments);
    }
    String ecLevel = decoderResult.getECLevel();
    if (ecLevel != null) {
      result.putMetadata(ResultMetadataType.ERROR_CORRECTION_LEVEL, ecLevel);
    }
    if (decoderResult.hasStructuredAppend()) {
      result.putMetadata(ResultMetadataType.STRUCTURED_APPEND_SEQUENCE,
                         decoderResult.getStructuredAppendSequenceNumber());
      result.putMetadata(ResultMetadataType.STRUCTURED_APPEND_PARITY,
                         decoderResult.getStructuredAppendParity());
    }

    return result;
  }
  //镜头放大
  private void doCameraZoom(DetectorResult detectorResult,BitMatrix bitMatrix){
    if(activity!=null && activity.isAutoEnlarged()){   //删除&&后面
      CameraManager cameraManager = activity.getCameraManager();
      ResultPoint[] p = detectorResult.getPoints();
      //计算扫描框中的二维码的宽度，两点间距离公式
      double point1X = p[0].getX();
      double point1Y = p[0].getY();
      double point2X = p[1].getX();
      double point2Y = p[1].getY();
      double point3X = p[2].getX();
      double point3Y = p[2].getY();

      double len12=  Math.sqrt(Math.abs(point1X-point2X)*Math.abs(point1X-point2X)+Math.abs(point1Y-point2Y)*Math.abs(point1Y-point2Y));
      double len13=  Math.sqrt(Math.abs(point1X-point3X)*Math.abs(point1X-point3X)+Math.abs(point1Y-point3Y)*Math.abs(point1Y-point3Y));
      double len23=  Math.sqrt(Math.abs(point2X-point3X)*Math.abs(point2X-point3X)+Math.abs(point2Y-point3Y)*Math.abs(point2Y-point3Y));
//      Toast.makeText( activity,"12:"+(int)len12+" 13:"+(int)len13+" 23:"+(int)len23,Toast.LENGTH_LONG ).show();
//      Toast.makeText( activity,point1X+" "+point1Y+" "+point2X+" "+point2Y,Toast.LENGTH_LONG ).show();
      double len =Math.max( len12,len13 );
      len =Math.max( len,len23 );

      Rect frameRect = cameraManager.getFramingRect();
      if(frameRect!=null){
        int frameWidth = frameRect.right-frameRect.left;
        int frameHeight=frameRect.bottom-frameRect.top;
        double frameCross= Math.sqrt( frameWidth*frameWidth+frameHeight*frameHeight );

//        Toast.makeText( activity,"黑色面积"+blackArea+"框面积"+maxI*maxJ,Toast.LENGTH_SHORT ).show();
//        Toast.makeText( activity,"宽度"+maxJ+"高度"+maxI+"框宽"+frameWidth+"框高"+frameHeight,Toast.LENGTH_SHORT ).show();
        Camera camera = cameraManager.getCamera();
        Camera.Parameters parameters = camera.getParameters();
//        int maxZoom = parameters.getMaxZoom();
        int zoom = parameters.getZoom();

//          Log.d("二维码边框长度",String.valueOf( len ));
//          Log.d("二维码边框长度",String.valueOf( len*Math.sqrt( zoom ) ));
//          Log.d("二维码边框长度",String.valueOf( len*zoom ));
//          Log.d("扫描框",String.valueOf( frameWidth ));
//        Toast.makeText( activity,"框对角线"+frameCross+"码长度"+len,Toast.LENGTH_SHORT ).show();
//        Log.d( "框对角线+码长度",frameCross+"+"+len*Math.sqrt( zoom ) );
//        Toast.makeText( activity,"relate"+len,Toast.LENGTH_SHORT ).show();
        if(parameters.isZoomSupported()){
          double relate;
          if(zoom==0){
            zoom++;
            relate=len/frameCross;
          }else{
            relate=len*Math.sqrt( zoom )/frameCross;
          }
//          Toast.makeText( activity,"relate:"+relate,Toast.LENGTH_SHORT ).show();

          if(relate<0.8){

            if(relate<0.3){
              zoom+=4;
            }else if(relate<0.45){
              zoom+=3;
            }else{
              if(len*Math.sqrt(zoom+1)<frameCross*0.8){
                zoom++;
              }
            }
//            Toast.makeText( activity,"relate:"+relate+" "+"zoom1:"+zoom,Toast.LENGTH_SHORT ).show();
            parameters.setZoom(zoom);
            camera.setParameters(parameters);
          }
        }
      }
    }
  }

  @Override
  public void reset() {
    // do nothing
  }

  /**
   * This method detects a code in a "pure" image -- that is, pure monochrome image
   * which contains only an unrotated, unskewed, image of a code, with some white border
   * around it. This is a specialized method that works exceptionally fast in this special
   * case.
   *
   * @see com.google.zxing.datamatrix.DataMatrixReader#extractPureBits(BitMatrix)
   */
  private static BitMatrix extractPureBits(BitMatrix image) throws NotFoundException {

    int[] leftTopBlack = image.getTopLeftOnBit();
    int[] rightBottomBlack = image.getBottomRightOnBit();
    if (leftTopBlack == null || rightBottomBlack == null) {
      throw NotFoundException.getNotFoundInstance();
    }

    float moduleSize = moduleSize(leftTopBlack, image);

    int top = leftTopBlack[1];
    int bottom = rightBottomBlack[1];
    int left = leftTopBlack[0];
    int right = rightBottomBlack[0];
    
    // Sanity check!
    if (left >= right || top >= bottom) {
      throw NotFoundException.getNotFoundInstance();
    }

    if (bottom - top != right - left) {
      // Special case, where bottom-right module wasn't black so we found something else in the last row
      // Assume it's a square, so use height as the width
      right = left + (bottom - top);
      if (right >= image.getWidth()) {
        // Abort if that would not make sense -- off image
        throw NotFoundException.getNotFoundInstance();
      }
    }

    int matrixWidth = Math.round((right - left + 1) / moduleSize);
    int matrixHeight = Math.round((bottom - top + 1) / moduleSize);
    if (matrixWidth <= 0 || matrixHeight <= 0) {
      throw NotFoundException.getNotFoundInstance();
    }
    if (matrixHeight != matrixWidth) {
      // Only possibly decode square regions
      throw NotFoundException.getNotFoundInstance();
    }

    // Push in the "border" by half the module width so that we start
    // sampling in the middle of the module. Just in case the image is a
    // little off, this will help recover.
    int nudge = (int) (moduleSize / 2.0f);
    top += nudge;
    left += nudge;
    
    // But careful that this does not sample off the edge
    // "right" is the farthest-right valid pixel location -- right+1 is not necessarily
    // This is positive by how much the inner x loop below would be too large
    int nudgedTooFarRight = left + (int) ((matrixWidth - 1) * moduleSize) - right;
    if (nudgedTooFarRight > 0) {
      if (nudgedTooFarRight > nudge) {
        // Neither way fits; abort
        throw NotFoundException.getNotFoundInstance();
      }
      left -= nudgedTooFarRight;
    }
    // See logic above
    int nudgedTooFarDown = top + (int) ((matrixHeight - 1) * moduleSize) - bottom;
    if (nudgedTooFarDown > 0) {
      if (nudgedTooFarDown > nudge) {
        // Neither way fits; abort
        throw NotFoundException.getNotFoundInstance();
      }
      top -= nudgedTooFarDown;
    }

    // Now just read off the bits
    BitMatrix bits = new BitMatrix(matrixWidth, matrixHeight);
    for (int y = 0; y < matrixHeight; y++) {
      int iOffset = top + (int) (y * moduleSize);
      for (int x = 0; x < matrixWidth; x++) {
        if (image.get(left + (int) (x * moduleSize), iOffset)) {
          bits.set(x, y);
        }
      }
    }
    return bits;
  }

  private static float moduleSize(int[] leftTopBlack, BitMatrix image) throws NotFoundException {
    int height = image.getHeight();
    int width = image.getWidth();
    int x = leftTopBlack[0];
    int y = leftTopBlack[1];
    boolean inBlack = true;
    int transitions = 0;
    while (x < width && y < height) {
      if (inBlack != image.get(x, y)) {
        if (++transitions == 5) {
          break;
        }
        inBlack = !inBlack;
      }
      x++;
      y++;
    }
    if (x == width || y == height) {
      throw NotFoundException.getNotFoundInstance();
    }
    return (x - leftTopBlack[0]) / 7.0f;
  }

}
