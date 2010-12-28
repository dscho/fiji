package fiji.plugin.trackmate.gui;

import ij.ImagePlus;
import ij.ImageStack;
import ij.gui.NewImage;
import ij.plugin.Duplicator;
import ij.process.ColorProcessor;
import ij.process.StackConverter;
import ij3d.Content;
import ij3d.ContentCreator;
import ij3d.Image3DUniverse;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.List;
import java.util.TreeMap;

import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.filechooser.FileNameExtensionFilter;

import org.jdom.DataConversionException;
import org.jdom.JDOMException;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.graph.SimpleGraph;

import loci.formats.FormatException;
import mpicbg.imglib.type.numeric.RealType;
import fiji.plugin.trackmate.Feature;
import fiji.plugin.trackmate.FeatureThreshold;
import fiji.plugin.trackmate.Logger;
import fiji.plugin.trackmate.Settings;
import fiji.plugin.trackmate.Spot;
import fiji.plugin.trackmate.TrackMate_;
import fiji.plugin.trackmate.io.TmXmlReader;
import fiji.plugin.trackmate.io.TmXmlWriter;
import fiji.plugin.trackmate.visualization.SpotDisplayer;
import fiji.plugin.trackmate.visualization.SpotDisplayer2D;
import fiji.plugin.trackmate.visualization.SpotDisplayer3D;
import fiji.plugin.trackmate.visualization.SpotDisplayer.TrackDisplayMode;



/**
* This code was edited or generated using CloudGarden's Jigloo
* SWT/Swing GUI Builder, which is free for non-commercial
* use. If Jigloo is being used commercially (ie, by a corporation,
* company or business for any purpose whatever) then you
* should purchase a license for each developer using Jigloo.
* Please visit www.cloudgarden.com for details.
* Use of Jigloo implies acceptance of these licensing terms.
* A COMMERCIAL LICENSE HAS NOT BEEN PURCHASED FOR
* THIS MACHINE, SO JIGLOO OR THIS CODE CANNOT BE USED
* LEGALLY FOR ANY CORPORATE OR COMMERCIAL PURPOSE.
*/
public class TrackMateFrame <T extends RealType<T>> extends javax.swing.JFrame {

	
	/**
	 * This is a helper class modified after a class by Albert Cardona
	 */
	private class DisplayUpdater extends Thread {
		long request = 0;

		// Constructor autostarts thread
		DisplayUpdater() {
			super("TrackMate displayer thread");
			setPriority(Thread.NORM_PRIORITY);
			start();
		}

		void doUpdate() {
			if (isInterrupted())
				return;
			synchronized (this) {
				request++;
				notify();
			}
		}

		void quit() {
			interrupt();
			synchronized (this) {
				notify();
			}
		}

		public void run() {
			while (!isInterrupted()) {
				try {
					final long r;
					synchronized (this) {
						r = request;
					}
					// Call displayer update from this thread
					if (r > 0)
						displayer.refresh(); // Is likely to generate NPE
					synchronized (this) {
						if (r == request) {
							request = 0; // reset
							wait();
						}
						// else loop through to update again
					}
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		}
	}
	
	
	
	static final Font FONT = new Font("Arial", Font.PLAIN, 10);
	static final Font SMALL_FONT = FONT.deriveFont(8);
	static final Dimension TEXTFIELD_DIMENSION = new Dimension(40,18);
	

	
	private static enum GuiState {
		START,
		TUNE_SEGMENTER,
		SEGMENTING,
		INITIAL_THRESHOLDING,
		CALCULATE_FEATURES, 
		THRESHOLD_BLOBS,
		TUNE_TRACKER,
		TRACKING,
		TUNE_DISPLAY;
	};
	
	private static final long serialVersionUID = 1L;

	private static final String START_DIALOG_KEY 			= "StartPanel";
	private static final String TUNE_SEGMENTER_KEY 			= "TuneSegmenterPanel";
	private static final String INITIAL_THRESHOLDING_KEY 	= "InitialThresholdingPanel";
	private static final String THRESHOLD_GUI_KEY 			= "ThresholdPanel";
	private static final String TUNE_TRACKER_KEY			= "TuneTrackerPanel";
	private static final String LOG_PANEL_KEY 				= "LogPanel";
	private static final String DISPLAYER_PANEL_KEY 		= "DisplayerPanel";
	
	private static final int DEFAULT_RESAMPLING_FACTOR = 3; // for the 3d viewer
	private static final int DEFAULT_THRESHOLD = 50; // for the 3d viewer
	private static final String DEFAULT_FILENAME = "TrackMateData.xml";
	private static final Icon NEXT_ICON = new ImageIcon(TrackMateFrame.class.getResource("images/arrow_right.png"));
	private static final Icon PREVIOUS_ICON = new ImageIcon(TrackMateFrame.class.getResource("images/arrow_left.png"));
	private static final Icon LOAD_ICON = new ImageIcon(TrackMateFrame.class.getResource("images/page_go.png"));
	private static final Icon SAVE_ICON = new ImageIcon(TrackMateFrame.class.getResource("images/page_save.png"));

	private TrackMate_<? extends RealType<?>> trackmate;
	private GuiState state;
	private Logger logger;
	private SpotDisplayer displayer;
	private File file;
	private DisplayUpdater updater = new DisplayUpdater();
	
	private StartDialogPanel startDialogPanel;
	private ThresholdGuiPanel thresholdGuiPanel;
	private LogPanel logPanel;
	private DisplayerPanel displayerPanel;
	private CardLayout cardLayout;
	private JButton jButtonSave;
	private JButton jButtonLoad;
	private JButton jButtonPrevious;
	private JButton jButtonNext;
	private JPanel jPanelButtons;
	private JPanel jPanelMain;
	private SegmenterSettingsPanel segmenterSettingsPanel;
	private TrackerSettingsPanel trackerSettingsPanel;
	private InitThresholdPanel initThresholdingPanel;
	private TreeMap<Integer, List<Spot>> rawSpots;
	
	
	{
		//Set Look & Feel
		try {
			javax.swing.UIManager.setLookAndFeel(javax.swing.UIManager.getSystemLookAndFeelClassName());
		} catch(Exception e) {
			e.printStackTrace();
		}
	}
	
	/*
	 * CONSTRUCTORS
	 */
	
	public TrackMateFrame(TrackMate_<T> plugin) {
		if (null == plugin)
			plugin = new TrackMate_<T>();
		this.trackmate = plugin;
		initGUI();
		logger = logPanel.getLogger();
		this.addWindowListener(new WindowAdapter() {
			@Override
			public void windowClosing(WindowEvent we) {
				updater.quit();
			}
		});
		state = GuiState.START;
	}
	
	public TrackMateFrame() {
		this(new TrackMate_<T>());
	}
	
	/**
	 * Called when the "Next >>" button is pressed.
	 */
	private void next() {
		switch(state) {
			case START:
				jButtonPrevious.setEnabled(true);
				state = GuiState.TUNE_SEGMENTER;
				execTuneSegmenter();
				break;
				
			case TUNE_SEGMENTER:
				execSegmentationStep();
				state = GuiState.SEGMENTING;
				break;
				
			case SEGMENTING:
				// Save the raw spot collection for later 
				rawSpots = trackmate.getSpots();
				// Make the initial threshold
				execInitThresholdingStep();
				state = GuiState.INITIAL_THRESHOLDING;
				break;
				
			case INITIAL_THRESHOLDING:
				execCalculateFeatures();
				state = GuiState.CALCULATE_FEATURES;
				break;
				
			case CALCULATE_FEATURES:
				execThresholdingStep();
				state = GuiState.THRESHOLD_BLOBS;
				break;
				
			case THRESHOLD_BLOBS:
				// Execute thresholding
				trackmate.setFeatureThresholds(thresholdGuiPanel.getFeatureThresholds());
				trackmate.execThresholding();
				displayer.setSpotsToShow(trackmate.getSelectedSpots());
				// Show tracker config panel 
				execTuneTracker();
				state = GuiState.TUNE_TRACKER;
				break;
				
			case TUNE_TRACKER:
				// Get settings from tuning panel and pass them to trackmate
				trackmate.getSettings().trackerSettings = trackerSettingsPanel.getSettings();
				// Track
				execTrackingStep();
				state = GuiState.TRACKING;
				break;
								
			case TRACKING:
				execShowDisplayerPanel();
				state = GuiState.TUNE_DISPLAY;
				jButtonNext.setEnabled(false);
				break;
				
		}
	}
	

	/**
	 * Called when the "<<" is pressed.
	 */
	private void previous() {		
		switch(state) {
					
		case TUNE_SEGMENTER:
			cardLayout.show(jPanelMain, START_DIALOG_KEY);
			jButtonPrevious.setEnabled(false);
			state = GuiState.START;
			break;
			
		case SEGMENTING:
			cardLayout.show(jPanelMain, TUNE_SEGMENTER_KEY);
			state = GuiState.TUNE_SEGMENTER;
			break;
			
		case INITIAL_THRESHOLDING:
			cardLayout.show(jPanelMain, LOG_PANEL_KEY);
			state = GuiState.SEGMENTING;
			break;
			
		case CALCULATE_FEATURES:
			// Restore the raw result in the trackmate
			trackmate.setSpots(rawSpots);
			// Display initial thresholding panel
			cardLayout.show(jPanelMain, INITIAL_THRESHOLDING_KEY);
			state = GuiState.INITIAL_THRESHOLDING;
			break;
			
		case THRESHOLD_BLOBS:
			cardLayout.show(jPanelMain, INITIAL_THRESHOLDING_KEY);
			state = GuiState.INITIAL_THRESHOLDING;
			break;
			
		case TUNE_TRACKER:
			cardLayout.show(jPanelMain, THRESHOLD_GUI_KEY);
			state =GuiState.THRESHOLD_BLOBS; 
			break;
			
		case TRACKING:
			cardLayout.show(jPanelMain, TUNE_TRACKER_KEY);
			state = GuiState.TUNE_TRACKER;
			break;
			
		case TUNE_DISPLAY:
			jButtonNext.setEnabled(true);
			cardLayout.show(jPanelMain, LOG_PANEL_KEY);
			state = GuiState.TRACKING;
	}
	}
	
	/**
	 * Called when the "Load" button is pressed.
	 */
	private void load() {
		jButtonLoad.setEnabled(false);
		if (null == file ) {
			File folder = new File(System.getProperty("user.dir")).getParentFile().getParentFile();
			file = new File(folder.getPath() + File.separator + DEFAULT_FILENAME);
		}
		JFileChooser fileChooser = new JFileChooser(file.getParent());
		fileChooser.setSelectedFile(file);
		FileNameExtensionFilter filter = new FileNameExtensionFilter("XML files", "xml");
		fileChooser.setFileFilter(filter);
		
		int returnVal = fileChooser.showOpenDialog(this);
		if(returnVal == JFileChooser.APPROVE_OPTION) {
			file = fileChooser.getSelectedFile();
		} else {
			logger.log("Load data aborted.\n");
			return;  	    		
		}
		
		cardLayout.show(jPanelMain, LOG_PANEL_KEY);
		logger.log("Opening file "+file.getName()+'\n');
		TmXmlReader reader = new TmXmlReader(file);
		try {
			reader.parse();
		} catch (JDOMException e) {
			logger.error("Problem parsing "+file.getName()+", it is not a valid TrackMate XML file.\nError message is:\n"+e.getLocalizedMessage()+'\n');
		} catch (IOException e) {
			logger.error("Problem reading "+file.getName()+".\nError message is:\n"+e.getLocalizedMessage()+'\n');
		}
		logger.log("  Parsing file done.\n");
		
		// Read settings
		Settings settings = null;
		try {
			settings = reader.getSettings();
		} catch (DataConversionException e1) {
			logger.error("Problem reading the settings field of "+file.getName()+". Error message is:\n"+e1.getLocalizedMessage()+'\n');
		}
		logger.log("  Reading settings done.\n");

		
		// Try to read image
		ImagePlus imp = null;
		try {
			imp = reader.getImage();
		} catch (IOException e) {
			logger.error("Problem loading the image linked by "+file.getName()+". Error message is:\n"+e.getLocalizedMessage()+'\n');
		} catch (FormatException e) {
			logger.error("Problem loading the image linked by "+file.getName()+". Error message is:\n"+e.getLocalizedMessage()+'\n');
		}
		if (null == imp) {
			// Provide a dummy empty image if linked image can't be found
			logger.log("Could not find image "+settings.imageFileName+" in "+settings.imageFolder+". Substituting dummy image.\n");
			imp = NewImage.createByteImage("Empty", settings.width, settings.height, settings.nframes * settings.nslices, NewImage.FILL_BLACK);
			imp.setDimensions(1, settings.nslices, settings.nframes);
		}
//		imp.show(); Launching the 2d displayer after that raise a NPE, I don't know why
		settings.imp = imp;
		trackmate.setSettings(settings);
		logger.log("  Reading image done.\n");
		
		// Try to read spots
		TreeMap<Integer, List<Spot>> spots = null;
		try {
			spots = reader.getAllSpots();
		} catch (DataConversionException e) {
			logger.error("Problem reading the spots field of "+file.getName()+". Error message is\n"+e.getLocalizedMessage()+'\n');
		}
		if (null == spots) {
			// No spots, so we stop here, and switch to the start panel
			if (null != startDialogPanel)
				jPanelMain.remove(startDialogPanel);
			startDialogPanel = new StartDialogPanel(trackmate.getSettings());
			jPanelMain.add(startDialogPanel, START_DIALOG_KEY);
			cardLayout.show(jPanelMain, START_DIALOG_KEY);
			state = GuiState.START;
			jButtonLoad.setEnabled(true);
			return;
		}
		// We have a spot field, so we can instantiate a new displayer
		trackmate.setSpots(spots);
		logger.log("  Reading spots done - launching displayer.\n");
		displayer = instantiateDisplayer(trackmate);
		// Also update the feature threshold GUI, in case the user move back to it
		thresholdGuiPanel.setSpots(spots.values());
		
		// Try to read spot selection
		TreeMap<Integer, List<Spot>> selectedSpots = null;
		try {
			selectedSpots = reader.getSpotSelection(spots);
		} catch (DataConversionException e) {
			logger.error("Problem reading the spot selection field of "+file.getName()+". Error message is\n"+e.getLocalizedMessage()+'\n');
		}
		if (null == selectedSpots) {
			// No spot selection, so we go to the thresholding panel
			execThresholdingStep();
			state = GuiState.THRESHOLD_BLOBS;
			jButtonLoad.setEnabled(true);
			return;
		}
		trackmate.setSpotSelection(selectedSpots);
		logger.log("  Reading spot selection done.\n");
		displayer.setSpotsToShow(selectedSpots);
		
		SimpleGraph<Spot, DefaultEdge> trackGraph = null; 
		try {
			trackGraph = reader.getTracks(selectedSpots);
		} catch (DataConversionException e) {
			logger.error("Problem reading the track field of "+file.getName()+". Error message is\n"+e.getLocalizedMessage()+'\n');
		}
		if (null == trackGraph) {
			// No track so we go to the tracking panel
			execTuneTracker();
			state = GuiState.TUNE_TRACKER;
			jButtonLoad.setEnabled(true);
			return;
		}
		logger.log("  Reading tracks done.\n");
		displayer.setTrackGraph(trackGraph);
		trackmate.setTrackGraph(trackGraph);
		state = GuiState.TRACKING;
		jButtonLoad.setEnabled(true);
		return;
		
	}
	
	/**
	 * Called when the "Save" button is pressed.
	 */
	private void save() {
		jButtonSave.setEnabled(false);
		if (null == file ) {
			File folder = new File(System.getProperty("user.dir")).getParentFile().getParentFile();
			file = new File(folder.getPath() + File.separator + DEFAULT_FILENAME);
		}
		JFileChooser fileChooser = new JFileChooser(file.getParent());
		fileChooser.setSelectedFile(file);
		FileNameExtensionFilter filter = new FileNameExtensionFilter("XML files", "xml");
		fileChooser.setFileFilter(filter);

		int returnVal = fileChooser.showSaveDialog(this);
		if(returnVal == JFileChooser.APPROVE_OPTION) {
			file = fileChooser.getSelectedFile();
		} else {
			logger.log("Save data aborted.\n");
			jButtonSave.setEnabled(true);
			return;  	    		
		}
		
		TmXmlWriter writer = new TmXmlWriter(trackmate);
		try {
			writer.writeToFile(file);
			logger.log("Data saved to: "+file.toString()+'\n');
		} catch (FileNotFoundException e) {
			logger.error("File not found:\n"+e.getMessage()+'\n');
		} catch (IOException e) {
			logger.error("Input/Output error:\n"+e.getMessage()+'\n');
		} finally {
			jButtonSave.setEnabled(true);
		}
	}


	
	/**
	 * Create a new {@link SegmenterSettingsPanel}.
	 */
	private void execTuneSegmenter() {
		trackmate.setSettings(startDialogPanel.getSettings());
		if (null != segmenterSettingsPanel)
			jPanelMain.remove(segmenterSettingsPanel);
		segmenterSettingsPanel = trackmate.getSettings().createSegmenterSettingsPanel();
		jPanelMain.add(segmenterSettingsPanel, TUNE_SEGMENTER_KEY);
		cardLayout.show(jPanelMain, TUNE_SEGMENTER_KEY);
	}
	
	private void execTuneTracker() {
		if (null != trackerSettingsPanel)
			jPanelMain.remove(trackerSettingsPanel);
		trackerSettingsPanel = trackmate.getSettings().createTrackerSettingsPanel();
		jPanelMain.add(trackerSettingsPanel, TUNE_TRACKER_KEY);
		cardLayout.show(jPanelMain, TUNE_TRACKER_KEY);
	}
	
	
	/**
	 * Switch to the log panel, and execute the segmentation step, which will be delegated to 
	 * the {@link TrackMate_} glue class in a new Thread.
	 */
	private void execSegmentationStep() {
		cardLayout.show(jPanelMain, LOG_PANEL_KEY);
		trackmate.getSettings().segmenterSettings = segmenterSettingsPanel.getSettings();
		logger.log("Starting segmentation...\n", Logger.BLUE_COLOR);
		logger.log("with settings:\n");
		logger.log(trackmate.getSettings().toString());
		logger.log(trackmate.getSettings().segmenterSettings.toString());
		new Thread("TrackMate segmentation thread") {					
			public void run() {
				long start = System.currentTimeMillis();
				try {
					trackmate.setLogger(logger);
					jButtonNext.setEnabled(false);
					trackmate.execSegmentation();
				} catch (Exception e) {
					logger.error("An error occured:\n"+e+'\n');
					e.printStackTrace(logger);
				} finally {
					jButtonNext.setEnabled(true);
					long end = System.currentTimeMillis();
					logger.log(String.format("Segmentation done in %.1f s.\n", (end-start)/1e3f), Logger.BLUE_COLOR);
				}
			}
		}.start();
	}
	
	
	/**
	 * Collect the segmentation result, and threshold it without rendering spots. 
	 */
	private void execInitThresholdingStep() {
		// Grab the quality feature value
		EnumMap<Feature, double[]> features = new EnumMap<Feature, double[]>(Feature.class);
		Collection<List<Spot>> clspots = trackmate.getSpots().values();
		int spotNumber = 0;
		for(Collection<? extends Spot> collection : clspots)
			spotNumber += collection.size();
		double[] values = new double[spotNumber];
		int index = 0;
		double val;
		for(Collection<? extends Spot> collection : clspots) {
			for (Spot spot : collection) {
				val = spot.getFeature(Feature.QUALITY);
				values[index] = val;
				index++;
			}
		}
		features.put(Feature.QUALITY, values);
		
		if (null != initThresholdingPanel)
			jPanelMain.remove(initThresholdingPanel);
		initThresholdingPanel = new InitThresholdPanel(features);
		jPanelMain.add(initThresholdingPanel, INITIAL_THRESHOLDING_KEY);
		cardLayout.show(jPanelMain, INITIAL_THRESHOLDING_KEY);
	}
	
	/**
	 * Collect the raw result, threshold by the quality value set in the init. threshold panel, 
	 * and then compute all features.
	 */
	private void execCalculateFeatures() {
		cardLayout.show(jPanelMain, LOG_PANEL_KEY);
		logger.log("Calculating features...\n",Logger.BLUE_COLOR);
		// Collect initial thresholding result
		FeatureThreshold ft = initThresholdingPanel.getFeatureThreshold();
		List<FeatureThreshold> featureThresholds = new ArrayList<FeatureThreshold>(1);
		featureThresholds.add(ft);
		TreeMap<Integer, List<Spot>> spots = TrackMate_.thresholdSpots(rawSpots, featureThresholds); // operate on the RAW segmentation result
		trackmate.setSpots(spots); // Here we OVERRIDE the raw results by this initially segmented collection
		// Calculate features
		trackmate.computeFeatures();		
		logger.log("Calculating features done.\n", Logger.BLUE_COLOR);
	}
	
	/**
	 * Render spots in another thread, then switch to the thresholding panel. 
	 */
	private void execThresholdingStep() {
		// Launch renderer
		logger.log("Rendering results...\n",Logger.BLUE_COLOR);
		jButtonNext.setEnabled(false);
		// Thread for rendering
		new Thread("TrackMate rendering thread") {
			public void run() {
				displayer = instantiateDisplayer(trackmate);
				cardLayout.show(jPanelMain, THRESHOLD_GUI_KEY);
				
				thresholdGuiPanel.setSpots(trackmate.getSpots().values());
				thresholdGuiPanel.addThresholdPanel(Feature.QUALITY);
				thresholdGuiPanel.addChangeListener(new ChangeListener() {
					@Override
					public void stateChanged(ChangeEvent e) {
						// Threshold spots
						trackmate.setFeatureThresholds(thresholdGuiPanel.getFeatureThresholds());
						trackmate.execThresholding();
						// Send to displayer
						displayer.setSpotsToShow(trackmate.getSelectedSpots());
						updater.doUpdate();
					}
				});
				thresholdGuiPanel.stateChanged(null);
				logger.log("Rendering done.\n", Logger.BLUE_COLOR);
				jButtonNext.setEnabled(true);
			}
		}.start();
	}
	
	/**
	 * Switch to the log panel, and execute the tracking part in another thread.
	 */
	private void execTrackingStep() {
		cardLayout.show(jPanelMain, LOG_PANEL_KEY);
		jButtonNext.setEnabled(false);
		logger.log("Starting tracking...\n", Logger.BLUE_COLOR);
		logger.log("with settings:\n");
		logger.log(trackmate.getSettings().trackerSettings.toString());
		new Thread("TrackMate tracking thread") {					
			public void run() {
				long start = System.currentTimeMillis();
				trackmate.execTracking();
				displayer.setTrackGraph(trackmate.getTrackGraph());
				displayer.setDisplayTrackMode(TrackDisplayMode.ALL_WHOLE_TRACKS, 20);
				updater.doUpdate();
				// Re-enable the GUI
				jButtonNext.setEnabled(true);
				long end = System.currentTimeMillis();
				logger.log(String.format("Tracking done in %.1f s.\n", (end-start)/1e3f), Logger.BLUE_COLOR);
			}
		}.start();
	}
	
	
	/**
	 * Create and switch to a displayer settings panel.
	 */
	private void execShowDisplayerPanel() {
		if (null != displayerPanel)
			jPanelMain.remove(displayerPanel);
		displayerPanel = new DisplayerPanel(trackmate.getSpots().values());
		displayerPanel.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				if (e == displayerPanel.TRACK_DISPLAY_MODE_CHANGED) {
					displayer.setDisplayTrackMode(displayerPanel.getTrackDisplayMode(), displayerPanel.getTrackDisplayDepth());
				} else if (e == displayerPanel.TRACK_VISIBILITY_CHANGED) {
					displayer.setTrackVisible(displayerPanel.isDisplayTrackSelected());
				} else if (e == displayerPanel.SPOT_VISIBILITY_CHANGED) {
					displayer.setSpotVisible(displayerPanel.isDisplaySpotSelected());
				} else if (e == displayerPanel.SPOT_COLOR_MODE_CHANGED) {
					displayer.setColorByFeature(displayerPanel.getColorSpotByFeature());
				} else {
					System.out.println("Unplanned event caught: "+e);
				}
				updater.doUpdate();
			}
		});
		jPanelMain.add(displayerPanel, DISPLAYER_PANEL_KEY);
		cardLayout.show(jPanelMain, DISPLAYER_PANEL_KEY);
	}
	
	/**
	 * Ensure an 8-bit gray image is sent to the 3D viewer.
	 */
	public static final ImagePlus[] makeImageForViewer(final Settings settings) {
		final ImagePlus origImp = settings.imp;
		origImp.killRoi();
		final ImagePlus imp;
		
		if (origImp.getType() == ImagePlus.GRAY8)
			imp = origImp;
		else {
			imp = new Duplicator().run(origImp);
			new StackConverter(imp).convertToGray8();
		}
		
		int nChannels = imp.getNChannels();
		int nSlices = settings.nslices;
		int nFrames = settings.nframes;
		ImagePlus[] ret = new ImagePlus[nFrames];
		int w = imp.getWidth(), h = imp.getHeight();

		ImageStack oldStack = imp.getStack();
		String oldTitle = imp.getTitle();
		
		for(int i = 0; i < nFrames; i++) {
			
			ImageStack newStack = new ImageStack(w, h);
			for(int j = 0; j < nSlices; j++) {
				int index = imp.getStackIndex(1, j+1, i+settings.tstart+1);
				Object pixels;
				if (nChannels > 1) {
					imp.setPositionWithoutUpdate(1, j+1, i+1);
					pixels = new ColorProcessor(imp.getImage()).getPixels();
				}
				else
					pixels = oldStack.getPixels(index);
				newStack.addSlice(oldStack.getSliceLabel(index), pixels);
			}
			ret[i] = new ImagePlus(oldTitle	+ " (frame " + i + ")", newStack);
			ret[i].setCalibration(imp.getCalibration().copy());
			
		}
		return ret;
	}
	
	private void initGUI() {
		try {
			setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
			this.setTitle(TrackMate_.PLUGIN_NAME_STR + " v"+TrackMate_.PLUGIN_NAME_VERSION);
			this.setResizable(false);
			{
				jPanelMain = new JPanel();
				cardLayout = new CardLayout();
				getContentPane().add(jPanelMain, BorderLayout.CENTER);
				jPanelMain.setLayout(cardLayout);
				jPanelMain.setPreferredSize(new java.awt.Dimension(300, 461));
			}
			{
				jPanelButtons = new JPanel();
				getContentPane().add(jPanelButtons, BorderLayout.SOUTH);
				jPanelButtons.setLayout(null);
				jPanelButtons.setSize(300, 30);
				jPanelButtons.setPreferredSize(new java.awt.Dimension(300, 30));
				{
					jButtonNext = new JButton();
					jPanelButtons.add(jButtonNext);
					jButtonNext.setText("Next");
					jButtonNext.setIcon(NEXT_ICON);
					jButtonNext.setFont(FONT);
					jButtonNext.setBounds(216, 3, 76, 25);
					jButtonNext.addActionListener(new ActionListener() {
						public void actionPerformed(ActionEvent e) {
							next();
						}
					});
				}
				{
					jButtonPrevious = new JButton();
					jPanelButtons.add(jButtonPrevious);
					jButtonPrevious.setIcon(PREVIOUS_ICON);
					jButtonPrevious.setFont(FONT);
					jButtonPrevious.setBounds(177, 3, 40, 25);
					jButtonPrevious.addActionListener(new ActionListener() {
						public void actionPerformed(ActionEvent e) {
							previous();
						}
					});
				}
				{
					jButtonLoad = new JButton();
					jPanelButtons.add(jButtonLoad);
					jButtonLoad.setText("Load");
					jButtonLoad.setIcon(LOAD_ICON);
					jButtonLoad.setFont(FONT);
					jButtonLoad.setBounds(0, 2, 76, 25);
					jButtonLoad.addActionListener(new ActionListener() {
						public void actionPerformed(ActionEvent e) {
							load();
						}
					});
				}
				{
					jButtonSave = new JButton();
					jPanelButtons.add(jButtonSave);
					jButtonSave.setText("Save");
					jButtonSave.setIcon(SAVE_ICON);
					jButtonSave.setFont(FONT);
					jButtonSave.setBounds(75, 2, 78, 25);
					jButtonSave.addActionListener(new ActionListener() {
						public void actionPerformed(ActionEvent e) {
							save();
						}
					});
				}
			}
			pack();
			this.setSize(300, 520);
			{
				startDialogPanel = new StartDialogPanel(trackmate.getSettings());
				jPanelMain.add(startDialogPanel, START_DIALOG_KEY);
			}
			{
				logPanel = new LogPanel();
				jPanelMain.add(logPanel, LOG_PANEL_KEY);
			}
			{
				thresholdGuiPanel = new ThresholdGuiPanel();
				thresholdGuiPanel.addActionListener(new ActionListener() {
					public void actionPerformed(ActionEvent e) {
						displayer.setColorByFeature(thresholdGuiPanel.getColorByFeature());
						updater.doUpdate();
					} 
				});
				jPanelMain.add(thresholdGuiPanel, THRESHOLD_GUI_KEY);
			}
			cardLayout.show(jPanelMain, START_DIALOG_KEY);
			state = GuiState.START;
		} catch (Exception e) {
			e.printStackTrace();
		}
		repaint();
		validate();
	}
	
	private static <T extends RealType<T>> SpotDisplayer instantiateDisplayer(TrackMate_<T> plugin) {
		SpotDisplayer disp;
		Settings settings = plugin.getSettings();
		// Render image data
		boolean is3D = settings.imp.getNSlices() > 1;
		if (is3D) { 
			if (!settings.imp.isVisible())
				settings.imp.show();
			final Image3DUniverse universe = new Image3DUniverse();
			universe.show();
			ImagePlus[] images = makeImageForViewer(settings);
			Content imageContent = ContentCreator.createContent(
					settings.imp.getTitle(), 
					images, 
					Content.VOLUME, 
					DEFAULT_RESAMPLING_FACTOR, 
					0,
					null, 
					DEFAULT_THRESHOLD, 
					new boolean[] {true, true, true});
			// Render spots
			disp = new SpotDisplayer3D(universe, settings.segmenterSettings.expectedRadius); 							
			universe.addContentLater(imageContent);

		} else {
			disp = new SpotDisplayer2D(settings);
		}
		disp.setSpots(plugin.getSpots());
		disp.render();
		return disp;
	}
	


	/**
	 * Auto-generated main method to display this JFrame
	 */
	public static <T extends RealType<T>> void main(String[] args) {
		ij.ImageJ.main(args);
		SwingUtilities.invokeLater(new Runnable() {
			public void run() {
				TrackMate_<T> trackmate = new TrackMate_<T>();
				TrackMateFrame<T> inst = new TrackMateFrame<T>(trackmate);
				trackmate.setLogger(inst.logger);
				
				inst.setLocationRelativeTo(null);
				inst.setVisible(true);
			}
		});
	}
	
}
