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

package com.google.zxing.qrcode.detector;

import android.graphics.Rect;
import android.hardware.Camera;
import android.util.Log;
import android.widget.Toast;

import com.common.zxing.CaptureActivity;
import com.common.zxing.camera.CameraManager;
import com.google.zxing.DecodeHintType;
import com.google.zxing.FormatException;
import com.google.zxing.NotFoundException;
import com.google.zxing.ResultPoint;
import com.google.zxing.ResultPointCallback;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.common.DetectorResult;
import com.google.zxing.common.GridSampler;
import com.google.zxing.common.PerspectiveTransform;
import com.google.zxing.common.detector.MathUtils;
import com.google.zxing.qrcode.NumCount;
import com.google.zxing.qrcode.decoder.Version;

import java.util.Map;

/**
 * <p>Encapsulates logic that can detect a QR Code in an image, even if the QR Code
 * is rotated or skewed, or partially obscured.</p>
 *
 * @author Sean Owen
 */
public class Detector {

  private final BitMatrix image;
  private ResultPointCallback resultPointCallback;
  private CaptureActivity activity;



  public Detector(BitMatrix image,CaptureActivity activity) {
    this.image = image;
    this.activity=activity;

  }

  protected final BitMatrix getImage() {
    return image;
  }

  protected final ResultPointCallback getResultPointCallback() {
    return resultPointCallback;
  }

  private void doCameraZoom(){
    if(activity!=null && activity.isAutoEnlarged()){   //删除&&后面
      CameraManager cameraManager = activity.getCameraManager();

      Camera camera = cameraManager.getCamera();
      Camera.Parameters parameters = camera.getParameters();
      int zoom = parameters.getZoom();
      zoom+=2;
//      Toast.makeText( activity,"zoom2:"+zoom,Toast.LENGTH_SHORT ).show();
      parameters.setZoom(zoom);
      camera.setParameters(parameters);

    }
  }

  /**
   * <p>Detects a QR Code in an image.</p>
   *
   * @return {@link DetectorResult} encapsulating results of detecting a QR Code
   * @throws NotFoundException if QR Code cannot be found
   * @throws FormatException if a QR Code cannot be decoded
   */
  public DetectorResult detect() throws NotFoundException, FormatException {
    return detect(null);
  }

  /**
   * <p>Detects a QR Code in an image.</p>
   *
   * @param hints optional hints to detector
   * @return {@link DetectorResult} encapsulating results of detecting a QR Code
   * @throws NotFoundException if QR Code cannot be found
   * @throws FormatException if a QR Code cannot be decoded
   */
  public final DetectorResult detect(Map<DecodeHintType,?> hints) throws NotFoundException, FormatException {

    resultPointCallback = hints == null ? null :
        (ResultPointCallback) hints.get(DecodeHintType.NEED_RESULT_POINT_CALLBACK);

    FinderPatternFinder finder = new FinderPatternFinder(image, resultPointCallback,activity);  //扫描定位符
    FinderPatternInfo info = finder.find(hints);

    return processFinderPatternInfo(info);
  }

  protected final DetectorResult processFinderPatternInfo(FinderPatternInfo info)
      throws NotFoundException, FormatException {
  //三个定位符
    FinderPattern topLeft = info.getTopLeft();
    FinderPattern topRight = info.getTopRight();
    FinderPattern bottomLeft = info.getBottomLeft();

//    if(topLeft==null){
//      Log.d("左上定位符空","null");
//    }else{
//      Log.d("左上定位符空","not null");
//    }
//    if(topRight==null){
//      Log.d("右上定位符空","null");
//    }else{
//      Log.d("右上定位符空","not null");
//    }
//    if(bottomLeft==null){
//      Log.d("左下定位符空","null");
//    }else{
//      Log.d("左下定位符空","not null");
//    }

    float moduleSize = calculateModuleSize(topLeft, topRight, bottomLeft);
//    Toast.makeText( activity,"moduleSize："+moduleSize,Toast.LENGTH_SHORT ).show();
    if (moduleSize < 1.0f) {
//      moduleErrorNum++;
//      Toast.makeText( activity,"moduleErrorNum:"+moduleSize,Toast.LENGTH_SHORT ).show();

//      doCameraZoom();  //改
//      Toast.makeText( activity,"1",Toast.LENGTH_SHORT ).show();
      throw NotFoundException.getNotFoundInstance();    //改？
    }
    int dimension = computeDimension(topLeft, topRight, bottomLeft, moduleSize);
    Version provisionalVersion = Version.getProvisionalVersionForDimension(dimension);
    int modulesBetweenFPCenters = provisionalVersion.getDimensionForVersion() - 7;

    AlignmentPattern alignmentPattern = null;
    // Anything above version 1 has an alignment pattern
    if (provisionalVersion.getAlignmentPatternCenters().length > 0) {

      // Guess where a "bottom right" finder pattern would have been
      float bottomRightX = topRight.getX() - topLeft.getX() + bottomLeft.getX();
      float bottomRightY = topRight.getY() - topLeft.getY() + bottomLeft.getY();

      // Estimate that alignment pattern is closer by 3 modules 估计对齐模式更接近3个模块
      // from "bottom right" to known top left location 从“右下角”到已知的左上角位置
      float correctionToTopLeft = 1.0f - 3.0f / modulesBetweenFPCenters;
      int estAlignmentX = (int) (topLeft.getX() + correctionToTopLeft * (bottomRightX - topLeft.getX()));
      int estAlignmentY = (int) (topLeft.getY() + correctionToTopLeft * (bottomRightY - topLeft.getY()));

      // Kind of arbitrary -- expand search radius before giving up
      for (int i = 4; i <= 16; i <<= 1) {
        try {
          alignmentPattern = findAlignmentInRegion(moduleSize,
              estAlignmentX,
              estAlignmentY,
              i);
          break;
        } catch (NotFoundException re) {
          // try next round
        }
      }
      // If we didn't find alignment pattern... well try anyway without it 如果没有它，那就试试吧
    }

    PerspectiveTransform transform =
        createTransform(topLeft, topRight, bottomLeft, alignmentPattern, dimension);

    BitMatrix bits = sampleGrid(image, transform, dimension);

    ResultPoint[] points;
    if (alignmentPattern == null) {
      points = new ResultPoint[]{bottomLeft, topLeft, topRight};
    } else {
      points = new ResultPoint[]{bottomLeft, topLeft, topRight, alignmentPattern};
    }
    return new DetectorResult(bits, points);
  }

  private static PerspectiveTransform createTransform(ResultPoint topLeft,
                                                      ResultPoint topRight,
                                                      ResultPoint bottomLeft,
                                                      ResultPoint alignmentPattern,
                                                      int dimension) {
    float dimMinusThree = dimension - 3.5f;
    float bottomRightX;
    float bottomRightY;
    float sourceBottomRightX;
    float sourceBottomRightY;
    if (alignmentPattern != null) {
      bottomRightX = alignmentPattern.getX();
      bottomRightY = alignmentPattern.getY();
      sourceBottomRightX = dimMinusThree - 3.0f;
      sourceBottomRightY = sourceBottomRightX;
    } else {
      // Don't have an alignment pattern, just make up the bottom-right point
      bottomRightX = (topRight.getX() - topLeft.getX()) + bottomLeft.getX();
      bottomRightY = (topRight.getY() - topLeft.getY()) + bottomLeft.getY();
      sourceBottomRightX = dimMinusThree;
      sourceBottomRightY = dimMinusThree;
    }

    return PerspectiveTransform.quadrilateralToQuadrilateral(
        3.5f,
        3.5f,
        dimMinusThree,
        3.5f,
        sourceBottomRightX,
        sourceBottomRightY,
        3.5f,
        dimMinusThree,
        topLeft.getX(),
        topLeft.getY(),
        topRight.getX(),
        topRight.getY(),
        bottomRightX,
        bottomRightY,
        bottomLeft.getX(),
        bottomLeft.getY());
  }

  private static BitMatrix sampleGrid(BitMatrix image,
                                      PerspectiveTransform transform,
                                      int dimension) throws NotFoundException {

    GridSampler sampler = GridSampler.getInstance();
    return sampler.sampleGrid(image, dimension, dimension, transform);
  }

  /**
   * <p>Computes the dimension尺寸 (number of modules on a size) of the QR Code based on the position
   * of the finder patterns and estimated module size.</p>
   */
  private int computeDimension(ResultPoint topLeft,
                                      ResultPoint topRight,
                                      ResultPoint bottomLeft,
                                      float moduleSize) throws NotFoundException {
    int tltrCentersDimension = MathUtils.round(ResultPoint.distance(topLeft, topRight) / moduleSize);
    int tlblCentersDimension = MathUtils.round(ResultPoint.distance(topLeft, bottomLeft) / moduleSize);
    int dimension = ((tltrCentersDimension + tlblCentersDimension) / 2) + 7;
    switch (dimension & 0x03) { // mod 4
      case 0:
        dimension++;
        break;
        // 1? do nothing
      case 2:
        dimension--;
        break;
      case 3:
        dimension++;
        NumCount.dimensionNum++;
        if(NumCount.dimensionNum==10000){
          NumCount.dimensionNum=0;
        }
        boolean blackSmall=isBlackAreaSmall();
//        Toast.makeText(activity,"error:"+NumCount.dimensionNum+" "+blackSmall,Toast.LENGTH_SHORT).show();
        if(blackSmall&&NumCount.dimensionNum%3==0){
          doCameraZoom();
        }
        throw NotFoundException.getNotFoundInstance();
    }
    return dimension;
  }

  private boolean isBlackAreaSmall(){
    CameraManager cameraManager=activity.getCameraManager();
    Rect frameRect = cameraManager.getFramingRect();
    double totalArea=frameRect.width()*frameRect.height();
    int blackArea=0;
    int maxI = image.getHeight();
    int maxJ = image.getWidth();
    int iSkip = (3 * maxI) / (4 * 57);  //调小优化?
    for (int i = iSkip - 1; i < maxI; i++) {

      for (int j = 0; j < maxJ; j++) { //改j++ 不一定以1为间隔
        if (image.get( j, i )) {
          blackArea++;
        }
      }
    }

//    Toast.makeText(activity,"relate:"+1.0*blackArea/totalArea,Toast.LENGTH_SHORT).show();

    if(1.0*blackArea/totalArea<0.25){
//      if(NumCount.blackSmallNum==10000||NumCount.blackLargeNum==10000){
//        NumCount.blackSmallNum=0;
//        NumCount.blackLargeNum=0;
//      }
//      NumCount.blackSmallNum++;
//      if(NumCount.blackSmallNum%4==0){
        return true;
//      }
    }else{
      NumCount.blackLargeNum++;
    }
    return false;
  }

  /**
   * <p>Computes an average estimated module size based on estimated derived from the positions
   * of the three finder patterns.</p>
   *
   * @param topLeft detected top-left finder pattern center
   * @param topRight detected top-right finder pattern center
   * @param bottomLeft detected bottom-left finder pattern center
   * @return estimated module size
   */
  protected final float calculateModuleSize(ResultPoint topLeft,
                                            ResultPoint topRight,
                                            ResultPoint bottomLeft) {
    // Take the average
    return (calculateModuleSizeOneWay(topLeft, topRight) +
        calculateModuleSizeOneWay(topLeft, bottomLeft)) / 2.0f;
  }

  /**
   * <p>Estimates module size based on two finder patterns -- it uses
   * {@link #sizeOfBlackWhiteBlackRunBothWays(int, int, int, int)} to figure the
   * width of each, measuring along the axis between their centers.</p>
   */
  private float calculateModuleSizeOneWay(ResultPoint pattern, ResultPoint otherPattern) {
    float moduleSizeEst1 = sizeOfBlackWhiteBlackRunBothWays((int) pattern.getX(),
        (int) pattern.getY(),
        (int) otherPattern.getX(),
        (int) otherPattern.getY());
    float moduleSizeEst2 = sizeOfBlackWhiteBlackRunBothWays((int) otherPattern.getX(),
        (int) otherPattern.getY(),
        (int) pattern.getX(),
        (int) pattern.getY());
    if (Float.isNaN(moduleSizeEst1)) {
      return moduleSizeEst2 / 7.0f; //?
    }
    if (Float.isNaN(moduleSizeEst2)) {
      return moduleSizeEst1 / 7.0f;
    }
    // Average them, and divide by 7 since we've counted the width of 3 black modules,
    // and 1 white and 1 black module on either side. Ergo, divide sum by 14.
    return (moduleSizeEst1 + moduleSizeEst2) / 14.0f;
  }

  /**
   * See {@link #sizeOfBlackWhiteBlackRun(int, int, int, int)}; computes the total width of
   * a finder pattern by looking for a black-white-black run from the center in the direction
   * of another point (another finder pattern center), and in the opposite direction too.
   */
  private float sizeOfBlackWhiteBlackRunBothWays(int fromX, int fromY, int toX, int toY) {

    float result = sizeOfBlackWhiteBlackRun(fromX, fromY, toX, toY);

    // Now count other way -- don't run off image though of course
    float scale = 1.0f;
    int otherToX = fromX - (toX - fromX);
    if (otherToX < 0) {
      scale = fromX / (float) (fromX - otherToX);
      otherToX = 0;
    } else if (otherToX >= image.getWidth()) {
      scale = (image.getWidth() - 1 - fromX) / (float) (otherToX - fromX);
      otherToX = image.getWidth() - 1;
    }
    int otherToY = (int) (fromY - (toY - fromY) * scale);

    scale = 1.0f;
    if (otherToY < 0) {
      scale = fromY / (float) (fromY - otherToY);
      otherToY = 0;
    } else if (otherToY >= image.getHeight()) {
      scale = (image.getHeight() - 1 - fromY) / (float) (otherToY - fromY);
      otherToY = image.getHeight() - 1;
    }
    otherToX = (int) (fromX + (otherToX - fromX) * scale);

    result += sizeOfBlackWhiteBlackRun(fromX, fromY, otherToX, otherToY);

    // Middle pixel is double-counted this way; subtract 1
    return result - 1.0f;
  }

  /**
   * <p>This method traces a line from a point in the image, in the direction towards another point.
   * It begins in a black region, and keeps going until it finds white, then black, then white again.
   * It reports报告 the distance from the start to this point.</p>
   *
   * <p>This is used when figuring out how wide a finder pattern is定位符的宽度, when the finder pattern
   * may be skewed or rotated倾斜或旋转.</p>
   */
  private float sizeOfBlackWhiteBlackRun(int fromX, int fromY, int toX, int toY) {
    // Mild variant of Bresenham's algorithm;
    // see http://en.wikipedia.org/wiki/Bresenham's_line_algorithm
    boolean steep = Math.abs(toY - fromY) > Math.abs(toX - fromX);
    if (steep) {
      int temp = fromX;
      fromX = fromY;
      fromY = temp;
      temp = toX;
      toX = toY;
      toY = temp;
    }

    int dx = Math.abs(toX - fromX);
    int dy = Math.abs(toY - fromY);
    int error = -dx / 2; //控制倾斜
    int xstep = fromX < toX ? 1 : -1;  //步长
    int ystep = fromY < toY ? 1 : -1;

    // In black pixels, looking for white, first or second time.
    int state = 0;
    // Loop up until x == toX, but not beyond
    int xLimit = toX + xstep;
    for (int x = fromX, y = fromY; x != xLimit; x += xstep) {
      int realX = steep ? y : x;
      int realY = steep ? x : y;

      // Does current pixel mean we have moved white to black or vice versa反之亦然?
      // Scanning black in state 0,2 and white in state 1, so if we find the wrong
      // color, advance to next state or end if we are in state 2 already如果我们已经处于状态2，则前进到下一个状态或结束
      if ((state == 1) == image.get(realX, realY)) { // 黑false 0 白true 1 白false 1 黑true 2
        if (state == 2) {
          return MathUtils.distance(x, y, fromX, fromY);
        }
        state++;
      }

      error += dy;
      if (error > 0) {
        if (y == toY) {
          break;
        }
        y += ystep;
        error -= dx;
      }
    }
    // Found black-white-black; give the benefit of the doubt that the next pixel outside the image
    // is "white" so this last point at (toX+xStep,toY) is the right ending. This is really a
    // small approximation近似; (toX+xStep,toY+yStep) might be really correct. Ignore this.
    if (state == 2) {
      return MathUtils.distance(toX + xstep, toY, fromX, fromY);
    }
    // else we didn't find even black-white-black; no estimate is really possible
    return Float.NaN;
  }

  /**
   * <p>Attempts to locate an alignment pattern in a limited region of the image, which is
   * guessed to contain it. This method uses {@link AlignmentPattern}.</p>
   *
   * @param overallEstModuleSize estimated module size so far
   * @param estAlignmentX x coordinate of center of area probably containing alignment pattern
   * @param estAlignmentY y coordinate of above
   * @param allowanceFactor number of pixels in all directions to search from the center
   * @return {@link AlignmentPattern} if found, or null otherwise
   * @throws NotFoundException if an unexpected error occurs during detection
   */
  protected final AlignmentPattern findAlignmentInRegion(float overallEstModuleSize,
                                                         int estAlignmentX,
                                                         int estAlignmentY,
                                                         float allowanceFactor)
      throws NotFoundException {
    // Look for an alignment pattern (3 modules in size) around where it
    // should be
    int allowance = (int) (allowanceFactor * overallEstModuleSize);
    int alignmentAreaLeftX = Math.max(0, estAlignmentX - allowance);
    int alignmentAreaRightX = Math.min(image.getWidth() - 1, estAlignmentX + allowance);
    if (alignmentAreaRightX - alignmentAreaLeftX < overallEstModuleSize * 3) {
      throw NotFoundException.getNotFoundInstance();
    }

    int alignmentAreaTopY = Math.max(0, estAlignmentY - allowance);
    int alignmentAreaBottomY = Math.min(image.getHeight() - 1, estAlignmentY + allowance);
    if (alignmentAreaBottomY - alignmentAreaTopY < overallEstModuleSize * 3) {
      throw NotFoundException.getNotFoundInstance();
    }

    AlignmentPatternFinder alignmentFinder =
        new AlignmentPatternFinder(
            image,
            alignmentAreaLeftX,
            alignmentAreaTopY,
            alignmentAreaRightX - alignmentAreaLeftX,
            alignmentAreaBottomY - alignmentAreaTopY,
            overallEstModuleSize,
            resultPointCallback);
    return alignmentFinder.find();
  }

}
