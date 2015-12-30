package org.dg.main;


import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Scanner;

import org.dg.openAIL.IdPair;
import org.dg.openAIL.MapPosition;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.util.Pair;

public class LocalizationView extends SurfaceView implements SurfaceHolder.Callback{
	private static final String TAG = "LocalizationView";
	
	
	class DrawThread extends Thread{
		private static final String TAG = "drawThread";
		
		private static final float drawSize = 25.0f;
		
		private SurfaceHolder mSurfaceHolder;
		private Handler mHandler;
		private Context mContext;
		
		private boolean mRun = true;
		
        /** The drawable to use as the background of the animation canvas */
        private Bitmap mBackgroundImage, mLegend;
		
		/**
         * Current height of the surface/canvas.
         *
         * @see #setSurfaceSize
         */
        private int mCanvasHeight = 1;

        /**
         * Current width of the surface/canvas.
         *
         * @see #setSurfaceSize
         */
        private int mCanvasWidth = 1;
		private RectF mScratchRect;
		private Paint mLinePaint;
		
		/**
		 * 
		 * @param surfaceHolder
		 * @param context
		 * @param handler
		 */
		private List<Pair<Double, Double>> wifiScanLocations = new ArrayList<Pair<Double, Double>>();
		private List<Pair<Double, Double>> userLocations = new ArrayList<Pair<Double, Double>>();
		
		private List<Node> nodeLocations = new ArrayList<Node>();
		private List<Edge> edgeLocations = new ArrayList<Edge>();
		
		float scale = 0.0f;
		double centerX = 0.0f;

		double centerY = 0.0f;
		
		
		public double zoom = 1.0;
		
		// MAP
		double oldMapPixels2Metres = 1, mapPixels2Metres=1;
		double backgroundResizedPx2OriginalPx = 1;
		double originX=0, originY=0;
		
		DrawThread(SurfaceHolder surfaceHolder, Context context,
                Handler handler)
        {
			Log.d(TAG, "Created thread");
			
			// get handles to some important objects
            mSurfaceHolder = surfaceHolder;
            mHandler = handler;
            mContext = context;
            
			String mapName = "CM_ver3";
			String backgroundFilePath = String.format(Locale.getDefault(),
					Environment.getExternalStorageDirectory().toString()
							+ "/OpenAIL/PriorData/" + mapName
							+ "/cmbin.jpg");
			mBackgroundImage = BitmapFactory.decodeFile(backgroundFilePath);
        	
			
			String mapFilePath = String.format(Locale.getDefault(),
					Environment.getExternalStorageDirectory().toString()
							+ "/OpenAIL/PriorData/" + mapName
							+ "/cmbin.map");
			
			Scanner mapScanner = null;
			try {
				mapScanner = new Scanner(new BufferedReader(new FileReader(
						mapFilePath)));
				mapScanner.useLocale(Locale.US);
			} catch (FileNotFoundException e1) {
			}

			mapScanner.useDelimiter("\\s|\\n");
			while (mapScanner.hasNext())
			{
				String keyword = mapScanner.next();
				if ( keyword.compareTo("ORIGIN") == 0){
					originX = mapScanner.nextDouble();
					originY = mapScanner.nextDouble();
					double angle0 = mapScanner.nextDouble();
					
					Log.d(TAG, "ORIGIN is in (" + originX + ", " + originY + ") with angle = " + angle0);
				} else if ( keyword.compareTo("SCALE") == 0){
					oldMapPixels2Metres = mapScanner.nextDouble();
					Log.d(TAG, "SCALE is " + oldMapPixels2Metres);
				}
				else if ( keyword.compareTo("NODE") == 0){
					int id = mapScanner.nextInt();
					double px = mapScanner.nextDouble();
					double py = mapScanner.nextDouble();
					double x = mapScanner.nextDouble();
					double y = mapScanner.nextDouble();
					Log.d(TAG, "NODE id=" + id + " pixel pos (" + px + ", " + py + "), metre pos (" + x + ", " + y + ")");
					nodeLocations.add(new Node(id, px, py));
				}
				else if ( keyword.compareTo("EDGE") == 0){
					double idFrom = mapScanner.nextInt();
					double idTo = mapScanner.nextInt();
					Log.d(TAG, "EDGE from " + idFrom + " to " + idTo);
					
					Node first = null, second = null;
					for (Node n : nodeLocations) {
						if (n.getId() == idFrom)
							first = n;
						else if (n.getId() == idTo)
							second = n;
					}
					edgeLocations.add(new Edge(first,second));
					
				} else
					Log.d(TAG, "Unrecognized line tag");
				mapScanner.nextLine();
			}
			
        	Resources res = context.getResources();
            mLegend = BitmapFactory.decodeResource(res,
                    R.drawable.legend);
            

            
            
            mScratchRect = new RectF(0, 0, 0, 0);
            
            mLinePaint = new Paint();
            mLinePaint.setAntiAlias(true);
            mLinePaint.setARGB(255, 0, 0, 255);
                    
        }
		
		@Override
		public void run() {
			while (mRun) {
				Canvas c = null;
				try {
					c = mSurfaceHolder.lockCanvas(null);
					synchronized (mSurfaceHolder) {
						    
						doDraw(c);
					}
				} finally {
					// do this in a finally so that if an exception is thrown
					// during the above, we don't leave the Surface in an
					// inconsistent state
					if (c != null) {
						mSurfaceHolder.unlockCanvasAndPost(c);
					}
				}
				
				try {
					sleep(400, 0);
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		}
		
		 public void setSurfaceSize() {
			 setSurfaceSize(mCanvasWidth, mCanvasHeight);
		 }
		 /* Callback invoked when the surface dimensions change. */
        public void setSurfaceSize(int width, int height) {
            // synchronized to make sure these all change atomically
            synchronized (mSurfaceHolder) {
                mCanvasWidth = width;
                mCanvasHeight = height;
                
				recomputeDrawingScale();
				
	            double widthScale = (double) (width) / mBackgroundImage.getWidth();
	            double heightScale = (double) (height) / mBackgroundImage.getHeight();
	            
	            double minimumScale = Math.min(widthScale, heightScale);
	            
	            int wid = (int) (minimumScale * mBackgroundImage.getWidth() * zoom);
	            int hei = (int) (minimumScale * mBackgroundImage.getHeight() * zoom); 
	            
	            backgroundResizedPx2OriginalPx = minimumScale;
	            
	            Log.d(TAG, "setSurfaceSize: old=" + mBackgroundImage.getWidth() + " new=" + wid + " scale=" + minimumScale);
	            
	            // Update
	            mapPixels2Metres = oldMapPixels2Metres * backgroundResizedPx2OriginalPx;
	            centerX = originX * backgroundResizedPx2OriginalPx;
	            centerY = originY * backgroundResizedPx2OriginalPx;
	            
	            Log.d(TAG, "setSurfaceSiz origin: old=" + originX + " new=" + centerX);
	            
                mBackgroundImage = Bitmap.createScaledBitmap(
                        mBackgroundImage, wid, hei, true);
                
                
                mLegend = Bitmap.createScaledBitmap(
                		mLegend, width, mLegend.getHeight()  * width / mLegend.getWidth(), true);
            }
        }

		/**
		 * 
		 */
		private void recomputeDrawingScale() {
			// Find the interval of changes for X and Y
			double minX = 0.0, maxX = 0.0, minY = 0.0, maxY = 0.0;
			
			boolean start = true;
			synchronized (wifiScanLocations) {
				for (Pair<Double, Double> p : wifiScanLocations) {
					if (p.second < minX || start)
						minX = p.second;
					if (p.second > maxX || start)
						maxX = p.second;

					if (p.first < minY || start)
						minY = p.first;
					if (p.first > maxY || start)
						maxY = p.first;

					start = false;
				}
			}

//			synchronized (userLocations) {
//				for (Pair<Double, Double> p : userLocations) {
//					if (p.second < minX || start)
//						minX = p.second;
//					if (p.second > maxX || start)
//						maxX = p.second;
//
//					if (p.first < minY || start)
//						minY = p.first;
//					if (p.first > maxY || start)
//						maxY = p.first;
//
//					start = false;
//				}
//			}

			Log.d(TAG, "visX in metres: " + minX + " " + maxX);
			Log.d(TAG, "visY in metres: " + minY + " " + maxY);

			// Computing the scale of drawing
//			float scaleX = mCanvasWidth / (float) (maxX - minX);
//			float scaleY = mCanvasHeight / (float) (maxY - minY);

			//scale = (float) (Math.min(scaleX, scaleY) / 1.4);
			
			//float unknownX = (float) ((maxX - minX) /  (664.0-248.0) * 1049.0);
//			float unknownX = (float) ((maxX - minX) /  (877.0-248.0) * 1049.0);
//			float unknownY = (float) ((maxY - minY) / (2360.0-509.0) * 3049.0);
//			
//			float scaleX = Math.abs(mCanvasWidth / (float) (unknownX));
//			float scaleY = Math.abs(mCanvasHeight / (float) (unknownY));
//			scale = (float) (Math.min(scaleX, scaleY));
//			
//			centerX = (float) ((maxX + minX) / 2) + 67.0f/1049.0f * unknownX;
//			centerY = (float) ((maxY + minY) / 2) + 90.0f/3049.0f * unknownY; //- 39.0f/1049.0f * unknownY;
			
			
			//TODO: LAST ONE!
//			// Computing metres to px conversion
//			float metres2pixelsVisXScale = (float) ((maxX - minX) /  (877.0-248.0) );
//			float metres2pixelsVisYScale = (float) ((maxY - minY) / (2360.0-509.0) );
//			
//			// Taking into account than on screen we can have less pixels than in the image
//			float scaleX = Math.abs(mCanvasWidth / (float) (metres2pixelsVisXScale) / 1049.0f);
//			float scaleY = Math.abs(mCanvasHeight / (float) (metres2pixelsVisYScale) / 3049.0f);
//			scale = (float) (Math.min(scaleX, scaleY));
//			
//			// Value in metres at center
//			centerX = -(670.1f - 1049.0f/2)*metres2pixelsVisXScale;
//			centerY = (1430.0f -  3049.0f/2)* metres2pixelsVisYScale; //- 39.0f/1049.0f * unknownY;
				
//			centerX = originX / mapPixels2Metres;
//			centerY = originY / mapPixels2Metres;
		}
		
		public void setWiFiScanLocations(List<Pair<Double, Double>> _newWiFiScanLocations)
		{
			synchronized (wifiScanLocations) {
				wifiScanLocations.clear();
				for(Pair<Double, Double> item: _newWiFiScanLocations) 
				{
					Pair<Double, Double> tmp = new Pair<Double, Double>(item.first, item.second);
					wifiScanLocations.add( tmp );
				}
			}
			recomputeDrawingScale();
		}
		
		public void setUserLocations(List<Pair<Double, Double>> _userLocations)
		{
			synchronized (userLocations) {
				userLocations.clear();
				for(Pair<Double, Double> item: _userLocations) 
				{
					Log.d(TAG, "User locations : " + item.first + " "  + item.second);
					Pair<Double, Double> tmp = new Pair<Double, Double>(item.first, item.second);
					userLocations.add( tmp );
				}
			}
			recomputeDrawingScale();
		}
		
		
		private void doDraw(Canvas canvas) {
			Log.d(TAG, "Called draw - wifiLocations: " + wifiScanLocations.size() + " userLocations: " + userLocations.size());
			Log.d(TAG, "Centers: " + centerX + " & " + centerY + " scale: " + scale);
			
			canvas.drawColor(Color.BLACK);
			
			// Draw the background image. Operations on the Canvas accumulate
			// so this is like clearing the screen.
			canvas.drawBitmap(mBackgroundImage, 0, 0, null);  
			
			canvas.drawBitmap(mLegend, canvas.getWidth() - mLegend.getWidth(), canvas.getHeight() - mLegend.getHeight(), null);  
			
			drawX(canvas, (float) centerX, (float) centerY, drawSize, 255, 0, 0);
			
			
			for (Node n : nodeLocations) {
				float drawX = (float) ((n.getPx()*backgroundResizedPx2OriginalPx));
				float drawY = (float) ((n.getPy()*backgroundResizedPx2OriginalPx));
				drawCircle(canvas, drawX, drawY, drawSize/3, 255, 0, 0);
				Log.d(TAG, "nodeLocations: " + drawX + " & " + drawY);
			}
			
			for (Edge e : edgeLocations) {
				float firstX = (float) (e.from.getPx()*backgroundResizedPx2OriginalPx);
				float firstY = (float) (e.from.getPy()*backgroundResizedPx2OriginalPx);
				float secondX = (float) (e.to.getPx()*backgroundResizedPx2OriginalPx);
				float secondY = (float) (e.to.getPy()*backgroundResizedPx2OriginalPx);
				drawLine(canvas, firstX, firstY, secondX,
						secondY, 255, 0 , 0);
			}
			
			
//            drawX(canvas, 700, 700, xSize, 255, 0, 0);
//            drawLine(canvas, 800, 600, 800, 800, 0, 0, 255);
//            drawCircle(canvas, 800, 800, xSize, 0, 0, 255);
            
			
				// Draw known WiFi scan locations as red X
				synchronized (wifiScanLocations) {
					
					for (Pair<Double, Double> p : wifiScanLocations) {
						float drawX = (float) ((p.second - centerX) * scale + mCanvasWidth / 2);
						float drawY = (float) ((p.first - centerY) * (-scale) + mCanvasHeight / 2);
						
						drawX(canvas, drawX, drawY, drawSize, 255, 0, 0);
					}
				}

				// Draw locations as blue circles connected by blue lines
				synchronized (userLocations) {
					boolean firstLocation = true;
					float prevDrawX = 0.0f, prevDrawY = 0.0f;
					for (Pair<Double, Double> p : userLocations) {
						float drawX = (float) ((p.second - centerX) * scale + mCanvasWidth / 2);
						float drawY = (float) ((p.first - centerY) * (-scale) + mCanvasHeight / 2);

						// Log.d(TAG, "Called drawCircle: " + p.first + " " +
						// p.second + " | " + drawX + " " + drawY);
						drawCircle(canvas, drawX, drawY, drawSize, 99, 209, 244);

						if (!firstLocation) {
							drawLine(canvas, prevDrawX, prevDrawY, drawX,
									drawY, 99, 209, 244);
						} else
							firstLocation = false;

						prevDrawX = drawX;
						prevDrawY = drawY;
					}
				}
			
		}
		
		private void drawLine(Canvas canvas, float x1, float y1, float x2, float y2, int r, int g, int b) {
			canvas.save();
			
            Paint mLinePaint = new Paint();
            mLinePaint.setAntiAlias(true);
            mLinePaint.setARGB(255, r, g, b);
            
            canvas.drawLine(x1, y1, x2, y2, mLinePaint);

            canvas.restore();
		}
	
		/**
		 * @param canvas
		 * @param xSize
		 */
		private void drawCircle(Canvas canvas, float x, float y, float xSize, int r, int g, int b) {
			canvas.save();
			
            Paint mCirclePaint = new Paint();
            mCirclePaint.setAntiAlias(true);
            mCirclePaint.setARGB(255, r, g, b);
            
            canvas.drawCircle(x, y, xSize/2, mCirclePaint);

            canvas.restore();
		}

		/**
		 * @param canvas
		 * @param xSize
		 */
		private void drawX(Canvas canvas, float x, float y, float xSize, int r, int g, int b) {
			canvas.save();
			canvas.translate(x, y);
			
            Paint mXPaint = new Paint();
            RectF mRect = new RectF();
            mXPaint.setAntiAlias(true);
            mXPaint.setARGB(255, r, g, b);
            
            canvas.rotate(45);
            mRect.set(-0.71f*xSize, -0.16f*xSize, 0.71f*xSize, 0.16f*xSize);
            canvas.drawRect(mRect, mXPaint);
            mRect.set(0.16f*xSize, 0.71f*xSize, -0.16f*xSize, -0.71f*xSize);
            canvas.drawRect(mRect, mXPaint);
            
            canvas.restore();
		}
		
		 /**
         * Used to signal the thread whether it should be running or not.
         * Passing true allows the thread to run; passing false will shut it
         * down if it's already running. Calling start() after this was most
         * recently called with false will result in an immediate shutdown.
         *
         * @param b true to run, false to shut down
         */
        public void setRunning(boolean b) {
            mRun = b;
        }

	}
	
	
	private DrawThread drawThread;
	
	public LocalizationView(Context context, AttributeSet attrs) {
		super(context, attrs);

		// register our interest in hearing about changes to our surface
        SurfaceHolder holder = getHolder();
        holder.addCallback(this);

        // create thread only; it's started in surfaceCreated()
        drawThread = new DrawThread(holder, context, new Handler() {
            @Override
            public void handleMessage(Message m) {
            }
        });
	}
	
	public void setWiFiScanLocations( List<Pair<Double, Double>> _newWiFiScanLocations) {
		drawThread.setWiFiScanLocations(_newWiFiScanLocations);
	}
	
	public void setUserLocations( List<Pair<Double, Double>> _userLocations) {
		drawThread.setUserLocations(_userLocations);
	}

	@Override
    /* Callback invoked when the surface dimensions change. */
    public void surfaceChanged(SurfaceHolder holder, int format, int width,
            int height) {
        drawThread.setSurfaceSize(width, height);
    }

    /*
     * Callback invoked when the Surface has been created and is ready to be
     * used.
     */
	@Override
    public void surfaceCreated(SurfaceHolder holder) {
        // start the thread here so that we don't busy-wait in run()
        // waiting for the surface to be created
        drawThread.setRunning(true);
        drawThread.start();
    }

	@Override
	public void surfaceDestroyed(SurfaceHolder arg0) {
		drawThread.setRunning(false);
		try {
			drawThread.join();
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	@Override
	public boolean onTouchEvent(MotionEvent event) {
		
		drawThread.zoom += 0.1; 
		drawThread.setSurfaceSize();
		Log.d(TAG, "onTouch!");
		return false;
		
	}
}
