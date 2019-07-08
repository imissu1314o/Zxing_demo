1.曝光度调节： 根据光线传感器的lux值，调节相机曝光度
    package com.google.zxing.qrcode.QRCodeReader:67行

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

2.识别区域优化：扩大识别区域，扫描范围的边长为 扫描框的边长加上 扫描框左边缘到屏幕左边缘的距离的。识别区域越大，扫码效率越高。
package com.common.zxing.camera.Camera：354
    int setInt=rect.left/2;//调整该数值调整灵敏度
    return new PlanarYUVLuminanceSource(data, width, height, rect.left-setInt, rect.top-setInt, rect.width()+setInt, rect.height()+setInt, false);



3.缩短自动聚焦的时间间隔。
在AutoFocusManager 中，有一个变量，AUTO_FOCUS_INTERVAL_MS，在自动聚焦的时候会根据该变量设定的时间来睡眠。
package com.common.zxing.camera：39
private static final long AUTO_FOCUS_INTERVAL_MS = 1200L;

4.镜头自动缩放：


    4.1 package com.google.zxing.qrcode.QRCodeReader:160
      控制每个放大程度上的识别次数，提高识别率（因为再清晰的二维码一次识别成功的概率很低）
      if(scanNum<=10){
        scanNum++;
      }
      if(scanNum>10){  //控制每个放大程度上的检测次数
        doCameraZoom( detectorResult,image.getBlackMatrix() );//doCameraZoom方法做放大处理
        scanNum=0;
      }

    4.1的放大算法：
    package com.common.zxing.camera：211
    程序运行到这里说明 DetectorResult detectorResult = new Detector(image.getBlackMatrix(),activity).detect(hints); 执行成功并且没有抛出异常中断，
    说明能找到三个定位符，但是二维码还是不一定能识别，并且计算出来的三个定位符也有可能不准确；
    计算三个定位符两两之间的距离，取其中的最大值近似作为二维码的跨度，让这个值与扫描框的边长比较，来确定相机放大的倍数（即调整相机zoom值，相机zoom值的改动为面积的改动）

    4.2 package com.google.zxing.qrcode.detector.Detector;248
    computeDimension()方法：
    case 3 里面就是4.1中那个抛异常程序中断的地方，在远距离识别的时候，镜头识别不到二维码，导致程序不会执行到4.1的放大算法那边。不执行放大算法，导致永远捕捉不到二维码。
    因此，远距离识别的时候，需要在这个抛异常的地方之前加上镜头放大处理。由于近距离的识别或者二维码清楚的时候，可能会由于镜头抖动之类的原因，程序也会执行到这里，因此可以
    通过一个计数器来计算程序运行到case3的次数，没三次执行到这才进行放大处理，在镜头远看不起码的时候，镜头很容易执行case3；而距离近的时候，不容易执行case3。
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

    4.2的放大处理
    package com.google.zxing.qrcode.detector.Detector;69
    每次放大倍数加2，放大倍数不能过大，如果码的扫码框的边缘，放大倍数过大会导致码脱离扫码框
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

5.定位符缺失识别：
    定位符遮挡区域变大，定位符中心的小正方形只要存在一条长和一条宽即能识别（左上角定位符小正方形遮挡与微信相似，右上和左下角小正方形遮挡大于微信），
    zxing原本遮挡范围为必须不遮定位符中心点。
    修改FinderPatternFinder(定位符查找)类
    package com.google.zxing.qrcode.detector.FinderPatternFinder;576
    定位符确定采用逐行扫描二维码的流程，在定位符中任意穿过一条线，黑白黑白黑的比例近似为11311的时候，说明这块区域可能为定位符。
    handlePossibleCenter(int[] stateCount, int i, int j, boolean pureBarcode)方法
    首先计算定位符重心在水平方向上的坐标
    float centerJ = centerFromEnd(stateCount, j); //水平方向中心点坐标
    然后求垂直方向上的重心坐标：
    float centerI = crossCheckVertical(i, (int) centerJ, stateCount[2], stateCountTotal);  //求垂直方向中心坐标 改

  private float crossCheckVertical(int startI, int centerJ, int maxCount,
      int originalStateCountTotal) {
    BitMatrix image = this.image;

    int maxI = image.getHeight();
    int[] stateCount = getCrossCheckStateCount();

    int minJ=centerJ,maxJ=centerJ; //这两个值为中间黑正方形水平方向的边界值


    while(image.get( maxJ,startI )){
      maxJ++;
    }
    maxJ--;
    while(image.get( minJ,startI )){
      minJ--;
    }
    minJ++;
    Log.d( "J",minJ+" "+maxJ );

    loop:
    for(int k=minJ;k<=maxJ;k++){      //通过循环从中间小正方左边缘遍历到右边缘，寻找定位符重心垂直方向上的坐标，只有有一个成立即可，这样就能保证只要存在一条垂直方向上的黑线没有被遮挡，就行找出这个坐标
      // Start counting up from center
      int i = startI;  //垂直方向
      while (i >= 0 && image.get(k, i)) { //startI上边到白块以前的黑块数量  改？
        stateCount[2]++;
        i--;
      }
      if (i < 0) {  //改？
        resetStateCount( stateCount );
        continue loop;
      }
      while (i >= 0 && !image.get(k, i) && stateCount[1] <= maxCount) { //统计白块数量
        stateCount[1]++;
        i--;
      }
      // If already too many modules in this state or ran off the edge:
//      Log.d("1 and maxCount",stateCount[1]+" "+maxCount);
      if (i < 0 || stateCount[1] > maxCount) {
        resetStateCount( stateCount );
        continue loop;
      }
      while (i >= 0 && image.get(k, i) && stateCount[0] <= maxCount) { //统计外边框的黑块数量
        stateCount[0]++;
        i--;
      }
      if (stateCount[0] > maxCount) {
        resetStateCount( stateCount );
        continue loop;
      }

      // Now also count down from center
      i = startI + 1;
      while (i < maxI && image.get(k, i)) {
        stateCount[2]++;
        i++;
      }
      if (i == maxI) {
        resetStateCount( stateCount );
        continue loop;
      }
      while (i < maxI && !image.get(k, i) && stateCount[3] < maxCount) {
        stateCount[3]++;
        i++;
      }
      if (i == maxI || stateCount[3] >= maxCount) {
        resetStateCount( stateCount );
        continue loop;
      }
      while (i < maxI && image.get(k, i) && stateCount[4] < maxCount) {
        stateCount[4]++;
        i++;
      }
      if (stateCount[4] >= maxCount) {   //?
        resetStateCount( stateCount );
        continue loop;
      }

      // If we found a finder-pattern-like section, but its size is more than 40% different than
      // the original, assume it's a false positive
      int stateCountTotal = stateCount[0] + stateCount[1] + stateCount[2] + stateCount[3] +
              stateCount[4];
      if (5 * Math.abs(stateCountTotal - originalStateCountTotal) >= 2 * originalStateCountTotal) {  //倾斜误差范围  originalStateCountTotal横向相加 stateCountTotal纵向
        resetStateCount( stateCount );
        continue loop;
      }
      //检测是否满足11311比例
      if(foundPatternCross(stateCount)){
        return centerFromEnd(stateCount, i);   //是否得有一定宽度，即满足11311比例的数量，先假设否
      }else{
        resetStateCount( stateCount );
        continue loop;
      }
    }

    return Float.NaN;

  }



删除下句，重新计算水平方向重心坐标，因为这个算法是从从重心出发，重心区域可能为白色，算法要求从黑色区域出发
//      centerJ = crossCheckHorizontal((int) centerJ, (int) centerI, stateCount[2], stateCountTotal);  //错误，因为原先（centerJ，centerI）指向白区，可以删除，也可以需要修改centerJ，让远点指向黑区

删除对角线检测：因为对角线检测必须要求重心必须存在
      if (!Float.isNaN(centerJ) &&
          (!pureBarcode || true)) {  //对角线检验 改？ //pureBarcode一般为false   //原本true的位置为对角线检测







