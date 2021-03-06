package de.evosec.fotilo.library;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.squareup.picasso.Picasso;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Point;
import android.graphics.PorterDuff;
import android.hardware.Camera;
import android.media.ExifInterface;
import android.media.MediaActionSound;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.support.annotation.NonNull;
import android.support.v4.app.Fragment;
import android.view.Display;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.animation.RotateAnimation;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.RelativeLayout;
import android.widget.SeekBar;
import android.widget.TextView;

/**
 * A simple {@link Fragment} subclass. Activities that contain this fragment
 * must implement the {@link CamFragment.OnFragmentInteractionListener}
 * interface to handle interaction events. Use the
 * {@link CamFragment#newInstance} factory method to create an instance of this
 * fragment.
 */
public class CamFragment extends Fragment
        implements View.OnClickListener, Camera.PictureCallback,
        SeekBar.OnSeekBarChangeListener, View.OnTouchListener {

	private static final Logger LOG =
	        LoggerFactory.getLogger(CamFragment.class);
	private static final int REVIEW_PICTURES_ACTIVITY_REQUEST = 123;
	private static final int MEDIA_TYPE_IMAGE = 1;
	private SeekBar zoomBar;
	private final MediaActionSound sound = new MediaActionSound();
	private int maxPictures;
	private int picturesTaken;
	private ArrayList<String> pictures;
	private Intent resultIntent;
	private Bundle resultBundle;
	private Camera camera;
	private Preview preview;
	private ProgressDialog progress;
	private int maxZoomLevel;
	private int currentZoomLevel;
	private boolean safeToTakePicture = false;
	private View view;
	private int touchcounter = 0;
	private int currentZoomParameter;
	private boolean landscape;

	public CamFragment() {
		// load action sound to avoid latency for first play
		sound.load(MediaActionSound.SHUTTER_CLICK);
	}

	/**
	 * Use this factory method to create a new instance of this fragment using
	 * the provided parameters.
	 *
	 * @return A new instance of fragment CamFragment.
	 */
	public static CamFragment newInstance() {
		return new CamFragment();
	}

	@Override
	public void onPictureTaken(byte[] data, Camera camera) {
		// wenn maxPictures noch nicht erreicht
		if (picturesTaken < maxPictures || maxPictures == 0) {
			File pictureFile = getOutputMediaFile(MEDIA_TYPE_IMAGE);
			if (pictureFile == null) {
				LOG.debug(
				    "Konnte Daei nicht erstellen, Berechtigungen überprüfen");
				return;
			}
			@SuppressWarnings("resource")
			FileOutputStream fos = null;
			try {
				fos = new FileOutputStream(pictureFile);
				fos.write(data);
				fos.close();
				final Uri imageUri =
				        getImageContentUri(getContext(), pictureFile);
				if (!landscape) {
					ExifInterface ef =
					        new ExifInterface(pictureFile.toString());
					ef.setAttribute(ExifInterface.TAG_ORIENTATION, 90 + "");
					ef.saveAttributes();

					ContentValues values = new ContentValues();
					values.put(MediaStore.Images.Media.ORIENTATION, 90);
					getContext().getContentResolver().update(imageUri, values,
					    null, null);
				}
				if (imageUri != null) {
					pictures.add(imageUri.toString());
					showLastPicture(imageUri);
					picturesTaken++;

					if (maxPictures <= 0) {
						LOG.debug("Picture {} / {}", picturesTaken,
						    maxPictures);
					}
					displayPicturesTaken();
					// set Result
					resultBundle.putStringArrayList("pictures", pictures);
					resultIntent.putExtra("data", resultBundle);

					sendNewPictureBroadcast(imageUri);
					camera.startPreview();
					safeToTakePicture = true;
				}
			} catch (FileNotFoundException e) {
				LOG.debug("File not found", e);
			} catch (IOException e) {
				LOG.debug("Error accessing file", e);
			} finally {
				if (fos != null) {
					try {
						fos.close();
					} catch (IOException e) {
						LOG.debug("unable to close file output stream", e);
					}
				}
				progress.dismiss();
			}
		}

		// wenn maxPictures erreicht, Bildpfade zurückgeben
		if (picturesTaken == maxPictures) {
			LOG.debug("maxPictures erreicht");
			getActivity().setResult(Activity.RESULT_OK, resultIntent);
			finishActivity();
		}
	}

	private void finishActivity() {
		getActivity().getWindow().setFlags(
		    WindowManager.LayoutParams.FLAG_FORCE_NOT_FULLSCREEN,
		    WindowManager.LayoutParams.FLAG_FORCE_NOT_FULLSCREEN);
		getActivity().finish();
	}

	private void displayPicturesTaken() {
		TextView txtpicturesTaken =
		        getActivity().findViewById(R.id.picturesTaken);
		if (picturesTaken > 0) {
			txtpicturesTaken.setVisibility(View.VISIBLE);
		} else {
			txtpicturesTaken.setVisibility(View.INVISIBLE);
		}
		txtpicturesTaken.setText("" + picturesTaken);
	}

	private void showLastPicture(Uri imageUri) {
		ImageButton pictureReview =
		        getActivity().findViewById(R.id.pictureReview);
		Picasso.with(getContext()).load(imageUri).resize(100, 100).centerCrop()
		    .into(pictureReview);
		pictureReview.setOnClickListener(this);
		pictureReview.setVisibility(View.VISIBLE);
	}

	private void hideLastPictureButton() {
		ImageButton pictureReview =
		        getActivity().findViewById(R.id.pictureReview);
		pictureReview.setVisibility(View.INVISIBLE);
	}

	private void sendNewPictureBroadcast(Uri imageUri) {
		Intent intent = new Intent("com.android.camera.NEW_PICTURE");
		intent.setData(imageUri);
		getActivity().sendBroadcast(intent);
	}

	private static Uri getImageContentUri(Context context, File imageFile) {
		String filePath = imageFile.getAbsolutePath();
		@SuppressWarnings("resource")
		Cursor cursor = null;
		try {
			cursor = context.getContentResolver().query(
			    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
			    new String[] {MediaStore.Images.Media._ID},
			    MediaStore.Images.Media.DATA + "=? ", new String[] {filePath},
			    null);
			if (cursor != null && cursor.moveToFirst()) {
				int id = cursor
				    .getInt(cursor.getColumnIndex(MediaStore.MediaColumns._ID));
				Uri baseUri =
				        Uri.parse("content://media/external/images/media");
				return Uri.withAppendedPath(baseUri, "" + id);
			} else {
				if (imageFile.exists()) {
					ContentValues values = new ContentValues();
					values.put(MediaStore.Images.Media.DATA, filePath);
					return context.getContentResolver().insert(
					    MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
				} else {
					return null;
				}
			}
		} finally {
			if (cursor != null) {
				cursor.close();
			}
		}
	}

	private static File getOutputMediaFile(int type) {
		File storageDir =
		        new File(Environment.getExternalStoragePublicDirectory(
		            Environment.DIRECTORY_PICTURES), "Fotilo");

		// Wenn Verzeichnis nicht existiert, erstellen
		if (!storageDir.exists() && !storageDir.mkdirs()) {
			LOG.debug("Konnte Bilderverzeichnis nicht erstellen!");
			return null;
		}

		// Dateinamen erzeugen
		String timeStmp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
		    .format(new Date());
		File mediaFile;
		if (type == MEDIA_TYPE_IMAGE) {
			mediaFile = new File(storageDir.getPath() + File.separator + "IMG_"
			        + timeStmp + ".jpg");
		} else {
			return null;
		}

		return mediaFile;
	}

	private void initFlashmodes() {
		List<String> supportedFlashModes =
		        camera.getParameters().getSupportedFlashModes();
		if (supportedFlashModes != null) {

			setDefaultFlashmode();
			updateFlashModeIcon();

		}
	}

	public void changeFlashMode() {
		Camera.Parameters params = camera.getParameters();
		List<String> supportedFlashModes = params.getSupportedFlashModes();
		String flashMode;
		switch (params.getFlashMode()) {
		case Camera.Parameters.FLASH_MODE_AUTO:
			flashMode = Camera.Parameters.FLASH_MODE_ON;
			break;
		case Camera.Parameters.FLASH_MODE_ON:
			flashMode = Camera.Parameters.FLASH_MODE_OFF;
			break;
		case Camera.Parameters.FLASH_MODE_OFF:
			if (supportedFlashModes
			    .contains(Camera.Parameters.FLASH_MODE_RED_EYE)) {
				flashMode = Camera.Parameters.FLASH_MODE_RED_EYE;
			} else {
				flashMode = Camera.Parameters.FLASH_MODE_AUTO;
			}
			break;
		case Camera.Parameters.FLASH_MODE_RED_EYE:
			flashMode = Camera.Parameters.FLASH_MODE_AUTO;
			break;
		default:
			flashMode = Camera.Parameters.FLASH_MODE_AUTO;
			break;
		}
		params.setFlashMode(flashMode);
		camera.setParameters(params);
		updateFlashModeIcon();
	}

	private void setDefaultFlashmode() {
		Camera.Parameters params = camera.getParameters();
		if (camera.getParameters().getSupportedFlashModes()
		    .contains(Camera.Parameters.FLASH_MODE_AUTO)) {
			params.setFlashMode(Camera.Parameters.FLASH_MODE_AUTO);
			camera.setParameters(params);
		}
	}

	private void findOptimalPictureSize() {
		// Ausgewählte Auflösung von rufender Activity einstellen
		Intent i = getActivity().getIntent();
		Camera.Parameters params = camera.getParameters();
		int w = 0;
		int h = 0;
		double ratio = 0;
		w = i.getIntExtra("width", w);
		h = i.getIntExtra("height", h);
		ratio = i.getDoubleExtra("aspectratio", ratio);
		LOG.debug("w = {}; h = {}; ratio = {}", w, h, ratio);
		Camera.Size bestSize;
		if (w > 0 && h > 0) {
			// Mindestauflösung setzen
			findOptimalPictureSizeBySize(w, h);
		} else if (ratio > 0) {
			bestSize = getLargestResolutionByAspectRatio(
			    camera.getParameters().getSupportedPictureSizes(), ratio,
			    false);
			if (bestSize.width == camera.getParameters().getPreviewSize().width
			        && bestSize.height == camera.getParameters()
			            .getPreviewSize().height) {
				// Errormeldung keine Auflösung mit passendem Seitenverhältnis
				// gefunden
				String error =
				        "Fehler: Keine Auflösung mit diesem Seitenverhältnis verfügbar!";
				resultBundle.putString("error", error);
				resultIntent.putExtra("data", resultBundle);
				getActivity().setResult(Activity.RESULT_CANCELED, resultIntent);
				finishActivity();
				return;
			}
			configurePictureSize(bestSize, params);
			LOG.debug("{} x {}", bestSize.width, bestSize.height);
		} else {
			// keine Auflösung vorgegeben: höchste 4:3 Auflösung wählen
			configureLargestFourToThreeRatioPictureSize();
		}
	}

	public void configurePictureSize(Camera.Size size,
	        Camera.Parameters params) {
		params.setPictureSize(size.width, size.height);
		camera.setParameters(params);
		scalePreviewSize();
	}

	public void scalePreviewSize() {
		Camera.Size pictureSize = camera.getParameters().getPictureSize();
		LOG.debug("PictureSize = {} x {}", pictureSize.width,
		    pictureSize.height);
		double pictureRatio =
		        (double) pictureSize.width / (double) pictureSize.height;
		Camera.Size previewSize = getLargestResolutionByAspectRatio(
		    camera.getParameters().getSupportedPreviewSizes(), pictureRatio,
		    true);
		LOG.debug("PreviewSize = {} x {}", previewSize.width,
		    previewSize.height);
		configurePreviewSize(previewSize);
	}

	public Camera.Size getLargestResolutionByAspectRatio(
	        List<Camera.Size> sizes, double aspectRatio,
	        boolean previewResolution) {
		Display display = getActivity().getWindowManager().getDefaultDisplay();
		Point displaySize = new Point();
		display.getSize(displaySize);
		Camera.Size largestSize = camera.getParameters().getPreviewSize();
		largestSize.width = 0;
		largestSize.height = 0;
		for (Camera.Size size : sizes) {
			double ratio = (double) size.width / (double) size.height;
			if (Math.abs(ratio - aspectRatio) < 0.00000001
			        && size.width >= largestSize.width
			        && size.height >= largestSize.height) {
				if (previewResolution && size.width <= displaySize.x
				        && size.height <= displaySize.y) {
					largestSize = size;
				} else if (!previewResolution) {
					largestSize = size;
				}
			}
		}
		return largestSize;
	}

	public void configurePreviewSize(Camera.Size bestPreviewSize) {
		Display display = getActivity().getWindowManager().getDefaultDisplay();
		Camera.Parameters params = camera.getParameters();
		Point size = new Point();
		display.getSize(size);
		int screenWidth = size.x;
		int screenHeight = size.y;
		params.setPreviewSize(bestPreviewSize.width, bestPreviewSize.height);
		camera.setParameters(params);
		preview.getLayoutParams().width = bestPreviewSize.width;
		preview.getLayoutParams().height = bestPreviewSize.height;

		FrameLayout frameLayout = getView().findViewById(R.id.preview);
		RelativeLayout.LayoutParams layoutPreviewParams =
		        (RelativeLayout.LayoutParams) frameLayout.getLayoutParams();
		layoutPreviewParams.width = bestPreviewSize.width;
		layoutPreviewParams.height = bestPreviewSize.height;
		layoutPreviewParams.addRule(RelativeLayout.CENTER_IN_PARENT);
		frameLayout.setLayoutParams(layoutPreviewParams);

		LOG.debug("screenSize = {} x {}", screenWidth, screenHeight);
		LOG.debug("PreviewSize = {} x {}", bestPreviewSize.width,
		    bestPreviewSize.height);
	}

	private void configureLargestFourToThreeRatioPictureSize() {
		Camera.Parameters params = camera.getParameters();
		List<Camera.Size> supportedPictureSizes =
		        params.getSupportedPictureSizes();
		Camera.Size bestSize = params.getPictureSize();
		bestSize.width = 0;
		bestSize.height = 0;
		double fourToThreeRatio = 4.0 / 3.0;
		for (Camera.Size supportedSize : supportedPictureSizes) {
			if (Math
			    .abs((double) supportedSize.width / supportedSize.height
			            - fourToThreeRatio) == 0
			        && supportedSize.width >= bestSize.width
			        && supportedSize.height >= bestSize.height) {
				bestSize = supportedSize;
			}
		}
		params.setPictureSize(bestSize.width, bestSize.height);
		camera.setParameters(params);
		configurePictureSize(bestSize, params);
		LOG.debug("{} x {}", bestSize.width, bestSize.height);
	}

	private void findOptimalPictureSizeBySize(int w, int h) {
		Camera.Parameters params = camera.getParameters();
		double tempDiff;
		double diff = Integer.MAX_VALUE;
		Camera.Size bestSize = null;
		for (Camera.Size supportedSize : params.getSupportedPictureSizes()) {
			// nächst größere Auflösung suchen
			if (supportedSize.width >= w && supportedSize.height >= h) {
				// Pythagoras
				tempDiff = Math
				    .sqrt(Math.pow((double) supportedSize.width - w, 2)
				            + Math.pow((double) supportedSize.height - h, 2));
				// minimalste Differenz suchen
				if (tempDiff < diff) {
					diff = tempDiff;
					bestSize = supportedSize;
				}
			}
		}
		// beste Auflösung setzen
		if (bestSize != null) {
			configurePictureSize(bestSize, params);
			LOG.debug("{} x {} px", bestSize.width, bestSize.height);
		} else {
			// Fehlermeldung zurückgeben
			String error = "Fehler: Auflösung zu hoch!";
			resultBundle.putString("error", error);
			resultIntent.putExtra("data", resultBundle);
			getActivity().setResult(Activity.RESULT_CANCELED, resultIntent);
			finishActivity();
		}
	}

	public boolean onKeyUp(int keyCode) {
		if (keyCode == KeyEvent.KEYCODE_ZOOM_IN) {
			Camera.Parameters params = camera.getParameters();
			params.set("zoom-action", "zoom-stop");
			camera.setParameters(params);
			return true;
		} else if (keyCode == KeyEvent.KEYCODE_ZOOM_OUT) {
			Camera.Parameters params = camera.getParameters();
			params.set("zoom-action", "zoom-stop");
			camera.setParameters(params);
			return true;
		}
		return false;
	}

	public boolean onKeyDown(int keyCode) {
		Camera.Parameters params = camera.getParameters();
		updateCurrentZoomParameters();
		if (keyCode == KeyEvent.KEYCODE_BACK) {
			resultIntent.putExtra("data", resultBundle);
			getActivity().setResult(Activity.RESULT_CANCELED, resultIntent);
			finishActivity();
		} else if (keyCode == KeyEvent.KEYCODE_CAMERA) {
			if (safeToTakePicture) {
				takePicture();
			}
		} else if (keyCode == KeyEvent.KEYCODE_ZOOM_IN) {
			params.set("zoom-action", "optical-tele-start");
			camera.setParameters(params);
			zoomIn();
			zoomBar.setProgress(currentZoomParameter);
			return true;
		} else if (keyCode == KeyEvent.KEYCODE_ZOOM_OUT) {

			params.set("zoom-action", "optical-wide-start");
			camera.setParameters(params);
			zoomOut();
			zoomBar.setProgress(currentZoomParameter);
			return true;
		} else if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
			zoomIn();
			zoomBar.setProgress(currentZoomLevel);
			return true;
		} else if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
			zoomOut();
			zoomBar.setProgress(currentZoomLevel);
			return true;
		}
		return false;
	}

	private void takePicture() {
		// Anwender signalisieren, dass ein Bild aufgenommen wird
		progress = ProgressDialog.show(getActivity(), "Speichern",
		    "Bild wird gespeichert...");
		camera.takePicture(new Camera.ShutterCallback() {

			@Override
			public void onShutter() {
				sound.play(MediaActionSound.SHUTTER_CLICK);
			}

		}, null, this);
		safeToTakePicture = false;
	}

	private void initPreview() {
		DrawingView drawingView = getView().findViewById(R.id.drawingView);
		preview = new Preview(getContext(), camera, drawingView);
		FrameLayout frameLayout = getView().findViewById(R.id.preview);
		frameLayout.addView(preview);
		safeToTakePicture = true;
	}

	private void updateFlashModeIcon() {
		ImageButton btnFlashmode = getView().findViewById(R.id.btn_flashmode);
		if (camera.getParameters().getSupportedFlashModes() != null) {
			switch (camera.getParameters().getFlashMode()) {
			case Camera.Parameters.FLASH_MODE_AUTO:
				btnFlashmode
				    .setImageResource(R.drawable.ic_flash_auto_black_24dp);
				break;
			case Camera.Parameters.FLASH_MODE_ON:
				btnFlashmode
				    .setImageResource(R.drawable.ic_flash_on_black_24dp);
				break;
			case Camera.Parameters.FLASH_MODE_OFF:
				btnFlashmode
				    .setImageResource(R.drawable.ic_flash_off_black_24dp);
				break;
			case Camera.Parameters.FLASH_MODE_RED_EYE:
				btnFlashmode
				    .setImageResource(R.drawable.ic_remove_red_eye_black_24dp);
				break;
			default:
				break;
			}
		} else {
			btnFlashmode.setVisibility(View.INVISIBLE);
		}
	}

	private void releaseCamera() {
		if (camera != null) {
			camera.stopPreview();
			camera.setPreviewCallback(null);
			if (preview != null) {
				preview.getHolder().removeCallback(preview);
			}
			camera.release();
			camera = null;
			if (preview != null && preview.getCamera() != null) {
				preview.setCamera(camera);
			}
			LOG.debug("camera released");
		}
	}

	@Override
	public void onPause() {
		LOG.debug("onPause()");
		super.onPause();
		releaseCamera();
	}

	@Override
	public void onDestroyView() {
		LOG.debug("onDestroyView()");
		super.onDestroyView();
		releaseCamera();
	}

	@Override
	public void onResume() {
		super.onResume();
		Intent i = getActivity().getIntent();
		this.maxPictures = i.getIntExtra("maxPictures", maxPictures);
		LOG.debug("onResume() maxPictures = {}", maxPictures);
		if (camera == null) {
			camera = getCameraInstance();
			if (preview != null && preview.getCamera() == null) {
				preview.setCamera(camera);
			}

		}
	}

	public static Camera getCameraInstance() {
		Camera c = null;
		try {
			c = Camera.open();
		} catch (Exception ex) {
			// Camera in use or does not exist
			LOG.debug("Error: keine Kamera bekommen", ex);
		}
		if (c != null) {
			LOG.debug("camera opened");
			LOG.debug("Camera = {}", c);
		}
		return c;
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		LOG.debug("onCreate()");
		super.onCreate(savedInstanceState);
		resultIntent = new Intent();
		resultBundle = new Bundle();
		Intent i = getActivity().getIntent();
		// max. Anzahl Bilder von rufender Activity auslesen
		this.maxPictures = i.getIntExtra("maxPictures", maxPictures);

		LOG.debug("onCreate() maxPictures = {}", maxPictures);
		this.picturesTaken = 0;
		this.pictures = new ArrayList<>();
		camera = getCameraInstance();
		if (preview != null && preview.getCamera() == null) {
			preview.setCamera(camera);
		}
		if (camera != null) {
			Camera.Parameters parameters = camera.getParameters();
			parameters.set("mode", "smart-auto");
			camera.setParameters(parameters);
		}
	}

	@Override
	public View onCreateView(@NonNull LayoutInflater inflater,
	        ViewGroup container, Bundle savedInstanceState) {
		LOG.debug("onCreateView()");
		LOG.debug("CamFragment");
		view = inflater.inflate(R.layout.fragment_cam, container, false);
		Button btnPrivacy = view.findViewById(R.id.privacy);
		btnPrivacy.setOnClickListener(this);
		zoomBar = view.findViewById(R.id.seekBar);
		ImageButton btnFlashmode = view.findViewById(R.id.btn_flashmode);
		btnFlashmode.setOnClickListener(this);
		ImageButton btnCapture = view.findViewById(R.id.btn_capture);
		btnCapture.setOnClickListener(this);
		ImageButton btnZoomin = view.findViewById(R.id.btnZoomIn);
		btnZoomin.setOnTouchListener(this);
		ImageButton btnZoomOut = view.findViewById(R.id.btnZoomOut);
		btnZoomOut.setOnTouchListener(this);
		ImageButton btnToggle = view.findViewById(R.id.menuToggle);
		btnToggle.setOnClickListener(this);
		return view;
	}

	@Override
	public void onStart() {
		LOG.debug("onStart()");
		super.onStart();
		if (camera == null) {
			camera = getCameraInstance();
			if (preview != null && preview.getCamera() == null) {
				preview.setCamera(camera);
			}
		}
		if (camera != null && camera.getParameters().isZoomSupported()) {
			ImageButton btnZoomin = getView().findViewById(R.id.btnZoomIn);
			ImageButton btnZoomOut = getView().findViewById(R.id.btnZoomOut);
			zoomBar.setVisibility(View.VISIBLE);

			btnZoomin.setVisibility(View.VISIBLE);
			btnZoomOut.setVisibility(View.VISIBLE);
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN
			        && Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
				zoomBar.getProgressDrawable().setColorFilter(
				    getResources().getColor(R.color.uiElementBackground),
				    PorterDuff.Mode.SRC_IN);
				btnZoomin.getBackground().setColorFilter(
				    getResources().getColor(R.color.shadow),
				    PorterDuff.Mode.SRC_IN);
				btnZoomOut.getBackground().setColorFilter(
				    getResources().getColor(R.color.shadow),
				    PorterDuff.Mode.SRC_IN);
			}
		}

		updateFlashModeIcon();
		initPreview();
		findOptimalPictureSize();
		initFlashmodes();
		if (camera.getParameters().isZoomSupported()) {
			maxZoomLevel = camera.getParameters().getMaxZoom();
			currentZoomLevel = 0;
			zoomBar.setMax(maxZoomLevel);
			zoomBar.setOnSeekBarChangeListener(this);
		}
	}

	@Override
	public void onClick(View v) {

		if (v.getId() == R.id.btn_flashmode) {
			changeFlashMode();
		} else if (v.getId() == R.id.btn_capture) {
			if (safeToTakePicture) {
				takePicture();
			}
		} else if (v.getId() == R.id.pictureReview) {
			startReviewPicturesActivity();
		} else if (v.getId() == R.id.menuToggle) {
			showMenu();
		} else if (v.getId() == R.id.privacy) {
			Intent intent = new Intent(Intent.ACTION_VIEW,
			    Uri.parse("http://www.evosec.de/datenschutz"));
			intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
			getView().findViewById(R.id.privacy).setVisibility(View.INVISIBLE);
			startActivity(intent);
		}

	}

	private void showMenu() {
		Button btn = view.findViewById(R.id.privacy);
		if (btn.getVisibility() == View.VISIBLE) {
			btn.setVisibility(View.INVISIBLE);
		} else {
			btn.setVisibility(View.VISIBLE);
		}

	}

	@Override
	public void onActivityCreated(Bundle savedInstanceState) {
		super.onActivityCreated(savedInstanceState);
		if (savedInstanceState != null) {
			this.pictures = savedInstanceState.getStringArrayList("pictures");
			this.maxPictures = savedInstanceState.getInt("maxPictures");
			this.picturesTaken = this.pictures.size();
			if (picturesTaken > 0) {
				showLastPicture(
				    Uri.parse(this.pictures.get(picturesTaken - 1)));
			}
			displayPicturesTaken();
		}
	}

	@Override
	public void onSaveInstanceState(@NonNull Bundle outState) {
		outState.putStringArrayList("pictures", pictures);
		outState.putInt("maxPictures", maxPictures);
		outState.putInt("picturesTaken", picturesTaken);
		super.onSaveInstanceState(outState);
	}

	private void startReviewPicturesActivity() {
		Bundle bundle = new Bundle();
		Intent intent = new Intent(getActivity(), ReviewPicturesActivity.class);
		bundle.putStringArrayList("pictures", pictures);
		intent.putExtra("data", bundle);
		startActivityForResult(intent, REVIEW_PICTURES_ACTIVITY_REQUEST);
	}

	@Override
	public void onActivityResult(int requestCode, int resultCode, Intent data) {
		if (requestCode == REVIEW_PICTURES_ACTIVITY_REQUEST
		        && (resultCode == Activity.RESULT_OK
		                || resultCode == Activity.RESULT_FIRST_USER)) {
			Bundle bundle = data.getBundleExtra("data");
			this.pictures = bundle.getStringArrayList("pictures");
			this.picturesTaken = this.pictures.size();
			if (!pictures.isEmpty()) {
				showLastPicture(
				    Uri.parse(this.pictures.get(pictures.size() - 1)));
			} else {
				hideLastPictureButton();
			}
			displayPicturesTaken();
			if (resultCode == Activity.RESULT_FIRST_USER) {
				resultIntent.putExtra("data", resultBundle);
				getActivity().setResult(Activity.RESULT_CANCELED, resultIntent);
				finishActivity();
			}
		}
	}

	@Override
	public void onProgressChanged(SeekBar seekBar, int progress,
	        boolean fromUser) {
		Camera.Parameters params = camera.getParameters();
		updateCurrentZoomParameters();
		if (fromUser && camera != null) {
			if (progress > currentZoomParameter) {
				params.set("zoom-action", "fast-tele-start");// ZoomIn
			} else if (progress < currentZoomParameter) {
				params.set("zoom-action", "fast-wide-start");// ZoomOut
			}
			camera.setParameters(params);
			currentZoomLevel = progress;
			params.setZoom(progress);
			camera.setParameters(params);
			params.set("zoom-action", "zoom-stop");
			camera.setParameters(params);
			updateCurrentZoomParameters();
			zoomBar.setProgress(currentZoomParameter);
		}
	}

	@Override
	public void onStartTrackingTouch(SeekBar seekBar) {

	}

	@Override
	public void onStopTrackingTouch(SeekBar seekBar) {

	}

	@NonNull
	private void updateCurrentZoomParameters() {
		Camera.Parameters params = camera.getParameters();
		if (params.get("curr_zoom_level") != null) {
			currentZoomParameter =
			        Integer.parseInt(params.get("curr_zoom_level"));
		} else {
			currentZoomParameter = currentZoomLevel;
		}
	}

	@Override
	public boolean onTouch(View v, MotionEvent event) {
		Camera.Parameters params = camera.getParameters();
		updateCurrentZoomParameters();
		if (event.getAction() == MotionEvent.ACTION_DOWN) {
			touchcounter = 0;
			if (v.getId() == R.id.btnZoomIn) {
				params.set("zoom-action", "optical-tele-start");// ZoomIn
			} else if (v.getId() == R.id.btnZoomOut) {
				params.set("zoom-action", "optical-wide-start");// ZoomOut
			}
			camera.setParameters(params);
			v.setPressed(true);
		}
		if (v.isPressed()) {
			if (touchcounter % 2 == 0) {
				if (v.getId() == R.id.btnZoomIn) {
					zoomIn();
				} else if (v.getId() == R.id.btnZoomOut) {
					zoomOut();
				}
			}
			updateCurrentZoomParameters();
			zoomBar.setProgress(currentZoomParameter);
			touchcounter++;
		}
		if (event.getAction() == MotionEvent.ACTION_UP) {
			params.set("zoom-action", "zoom-stop");
			camera.setParameters(params);
			v.setPressed(false);
		}
		return false;
	}

	/**
	 * This interface must be implemented by activities that contain this
	 * fragment to allow an interaction in this fragment to be communicated to
	 * the activity and potentially other fragments contained in that activity.
	 * See the Android Training lesson <a href=
	 * "http://developer.android.com/training/basics/fragments/communicating.html"
	 * >Communicating with Other Fragments</a> for more information.
	 */
	public interface OnFragmentInteractionListener {

		void onFragmentInteraction(Uri uri);
	}

	public void zoomIn() {

		if (camera != null) {
			Camera.Parameters params = camera.getParameters();
			updateCurrentZoomParameters();
			if (currentZoomLevel < (maxZoomLevel)
			        && currentZoomParameter == currentZoomLevel) {
				currentZoomLevel++;
			} else {
				currentZoomLevel = maxZoomLevel;
			}
			params.setZoom(currentZoomLevel);
			camera.setParameters(params);
		}
	}

	public void zoomOut() {

		if (camera != null) {
			Camera.Parameters params = camera.getParameters();
			updateCurrentZoomParameters();
			if (currentZoomLevel > 0
			        && currentZoomParameter == currentZoomLevel) {
				currentZoomLevel--;
			} else {
				currentZoomLevel = 0;
			}
			params.setZoom(currentZoomLevel);
			camera.setParameters(params);
		}
	}

	public void rotateLandscape() {
		ArrayList<View> views = new ArrayList<>();
		views.add(view.findViewById(R.id.btn_flashmode));
		views.add(view.findViewById(R.id.btn_capture));
		views.add(view.findViewById(R.id.pictureReview));
		views.add(view.findViewById(R.id.btnZoomIn));
		views.add(view.findViewById(R.id.btnZoomOut));
		views.add(view.findViewById(R.id.menuToggle));
		views.add(view.findViewById(R.id.privacy));
		views.add(view.findViewById(R.id.picturesTaken));
		for (View btn : views) {
			if (btn != null) {
				if (btn.getVisibility() == View.VISIBLE) {
					RotateAnimation rotateAnimation = new RotateAnimation(270,
					    360, btn.getPivotX(), btn.getPivotY());
					rotateAnimation.setDuration(800);
					btn.startAnimation(rotateAnimation);
				}
				btn.setRotation(0);
			}
		}
		landscape = true;
		Camera.Parameters params = camera.getParameters();
		params.setRotation(0);
		camera.setParameters(params);
	}

	public void rotatePortrait() {
		ArrayList<View> views = new ArrayList<>();
		views.add(view.findViewById(R.id.btn_flashmode));
		views.add(view.findViewById(R.id.btn_capture));
		views.add(view.findViewById(R.id.pictureReview));
		views.add(view.findViewById(R.id.btnZoomIn));
		views.add(view.findViewById(R.id.btnZoomOut));
		views.add(view.findViewById(R.id.menuToggle));
		views.add(view.findViewById(R.id.privacy));
		views.add(view.findViewById(R.id.picturesTaken));

		for (View btn : views) {
			if (btn != null) {
				if (btn.getVisibility() == View.VISIBLE) {
					RotateAnimation rotateAnimation = new RotateAnimation(90, 0,
					    btn.getPivotX(), btn.getPivotY());
					rotateAnimation.setDuration(800);
					btn.startAnimation(rotateAnimation);
				}
				btn.setRotation(270);
			}
		}
		landscape = false;
		Camera.Parameters params = camera.getParameters();
		params.setRotation(90);
		camera.setParameters(params);
	}

}
