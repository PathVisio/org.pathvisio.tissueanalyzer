package org.pathvisio.tissueanalyzer;
//PathVisio,
//a tool for data visualization and analysis using Biological Pathways
//Copyright 2006-2011 BiGCaT Bioinformatics
//
//Licensed under the Apache License, Version 2.0 (the "License");
//you may not use this file except in compliance with the License.
//You may obtain a copy of the License at
//
//http://www.apache.org/licenses/LICENSE-2.0
//
//Unless required by applicable law or agreed to in writing, software
//distributed under the License is distributed on an "AS IS" BASIS,
//WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
//See the License for the specific language governing permissions and
//limitations under the License.
//


import java.awt.Color;
import java.awt.Component;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;
import java.util.Map.Entry;

import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingWorker;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

import org.bridgedb.DataSource;
import org.bridgedb.IDMapperException;
import org.bridgedb.rdb.construct.DBConnector;
import org.pathvisio.core.debug.Logger;
import org.pathvisio.core.preferences.GlobalPreference;
import org.pathvisio.core.preferences.PreferenceManager;
import org.pathvisio.core.util.FileUtils;
import org.pathvisio.core.util.ProgressKeeper;
import org.pathvisio.core.util.ProgressKeeper.ProgressEvent;
import org.pathvisio.core.util.ProgressKeeper.ProgressListener;
import org.pathvisio.data.DataException;
import org.pathvisio.data.DataInterface;
import org.pathvisio.data.ISample;
import org.pathvisio.desktop.PvDesktop;
import org.pathvisio.desktop.data.DBConnectorSwing;
import org.pathvisio.desktop.gex.Sample;
import org.pathvisio.desktop.visualization.ColorGradient;
import org.pathvisio.desktop.visualization.ColorGradient.ColorValuePair;
import org.pathvisio.desktop.visualization.ColorSet;
import org.pathvisio.desktop.visualization.ColorSetManager;
import org.pathvisio.desktop.visualization.Visualization;
import org.pathvisio.desktop.visualization.VisualizationManager;
import org.pathvisio.gexplugin.GexTxtImporter;
import org.pathvisio.gexplugin.ImportInformation;
import org.pathvisio.visualization.plugins.ColorByExpression;
import org.pathvisio.visualization.plugins.DataNodeLabel;

import com.jgoodies.forms.builder.DefaultFormBuilder;
import com.jgoodies.forms.builder.PanelBuilder;
import com.jgoodies.forms.layout.CellConstraints;
import com.jgoodies.forms.layout.FormLayout;
import com.nexes.wizard.Wizard;
import com.nexes.wizard.WizardPanelDescriptor;

/**
* Wizard to guide the user through importing data set from Expression Atlas
* in PathVisio.
* @author Jonathan Melius
*/
public class TissueWizard extends Wizard implements OberservableExperiment
{
	private ImportInformation importInformation;	

	private ArrayList<ObserverSidePanel> observers;

	private FilePage fpd;
	private TissuesPage tpd;
	private ImportPage ipd;
	private String experiment;
	private ArrayList<String> selectedTissues;

	private final PvDesktop standaloneEngine;

	public static final String first_experiment="E-MTAB-513";
	public static final String second_experiment="E-MTAB-1733";

	public TissueWizard (PvDesktop standaloneEngine)
	{
		this.standaloneEngine = standaloneEngine;
		importInformation = new ImportInformation();
		experiment="";
		fpd = new FilePage();
		tpd = new TissuesPage();
		ipd = new ImportPage();
		
		observers = new ArrayList<ObserverSidePanel>();

		getDialog().setTitle ("Atlas Expression data import wizard");
		registerWizardPanel(fpd);
		registerWizardPanel(tpd);
		registerWizardPanel(ipd);
		setCurrentPanel(FilePage.IDENTIFIER);
	}

	private class FilePage extends WizardPanelDescriptor implements ActionListener
	{
		public static final String IDENTIFIER = "FILE_PAGE";
		static final String ACTION_OUTPUT = "output";
		static final String ACTION_GDB = "gdb";

		private JTextField txtOutput;
		private JTextField txtGdb;
		private JButton btnGdb;
		private JButton btnOutput;
		private ButtonGroup group;
		boolean txtOutputComplete;

		public void aboutToDisplayPanel()
		{
			getWizard().setNextFinishButtonEnabled(txtOutputComplete);
			getWizard().setPageTitle ("Choose an experiments and the file locations");
		}

		public FilePage()
		{
			super(IDENTIFIER);
		}

		public Object getNextPanelDescriptor()
		{
			return TissuesPage.IDENTIFIER;
		}

		public Object getBackPanelDescriptor()
		{
			return null;
		}
		/**
		 * Check the text output field. If it's empty the user can't continue
		 */
		public void updateTxt(){
			if (txtOutput.getText().equals("")){
				txtOutputComplete=false;
			}
			else {				
				txtOutputComplete = true;					
			}
			getWizard().setNextFinishButtonEnabled(txtOutputComplete);
		}

		protected JPanel createContents()
		{
			txtOutput = new JTextField(40);
			txtGdb = new JTextField(40);
			btnGdb = new JButton ("Browse");;
			btnOutput = new JButton ("Browse");

			//The radio buttons.
			JRadioButton FirstRButton = new JRadioButton(second_experiment);
			FirstRButton.setSelected(true);
			FirstRButton.setActionCommand(second_experiment);
			JRadioButton SecondRButton = new JRadioButton(first_experiment);
			SecondRButton.setActionCommand(first_experiment);

			//Group the radio buttons.
			group = new ButtonGroup();
			group.add(FirstRButton);
			group.add(SecondRButton);

			FormLayout layout = new FormLayout (
					"right:pref, 3dlu, pref, 3dlu, pref",
					"p, 3dlu, p, 3dlu, p, 3dlu, p, 3dlu, p");

			PanelBuilder builder = new PanelBuilder(layout);
			builder.setDefaultDialogBorder();
			CellConstraints cc = new CellConstraints();			

			builder.add(FirstRButton,cc.xy (1,1));
			builder.addLabel ("RNA-seq of coding RNA from tissue samples"
					+ " representing 27 different tissues",
					cc.xy (3,1));			
			builder.add(SecondRButton,cc.xy (1,3));
			builder.addLabel ("RNA-Seq of human individual tissues "
					+ "and mixture of 16 tissues (Illumina Body Map)",
					cc.xy (3,3));
			builder.addLabel ("Output file", cc.xy (1,7));
			builder.add (txtOutput, cc.xy (3,7));
			builder.add (btnOutput, cc.xy (5,7));
			builder.addLabel ("Gene database", cc.xy (1,9));
			builder.add (txtGdb, cc.xy (3,9));
			builder.add (btnGdb, cc.xy (5,9));

			btnOutput.addActionListener(this);
			btnOutput.setActionCommand(ACTION_OUTPUT);
			btnGdb.addActionListener(this);
			btnGdb.setActionCommand(ACTION_GDB);

			txtGdb.setText(
					PreferenceManager.getCurrent()
					.get(GlobalPreference.DB_CONNECTSTRING_GDB)
					);
			txtOutput.getDocument().addDocumentListener(new DocumentListener()
			{
				public void changedUpdate(DocumentEvent arg0)
				{
					updateTxt();
				}

				public void insertUpdate(DocumentEvent arg0)
				{
					updateTxt();
				}

				public void removeUpdate(DocumentEvent arg0)
				{
					updateTxt();
				}

			});
			return builder.getPanel();
		}

		public void aboutToHidePanel()
		{
			String outFile = null;
			File f = new File(txtOutput.getText());
			try {
				f.getCanonicalPath();
				f=FileUtils.replaceExtension(f, "pgex");
				outFile = f.getCanonicalPath();
			} catch (IOException e) {
				e.printStackTrace();
			}	
			importInformation.setGexName (outFile);
			experiment = group.getSelection().getActionCommand();
		}

		public void actionPerformed(ActionEvent e) {
			String action = e.getActionCommand();

			if(ACTION_GDB.equals(action)) {
				standaloneEngine.selectGdb("Gene");
				txtGdb.setText(
						PreferenceManager.getCurrent()
						.get(GlobalPreference.DB_CONNECTSTRING_GDB)
						);
			}
			else if(ACTION_OUTPUT.equals(action)) {
				try {
					DBConnector dbConn = standaloneEngine.getGexManager().getDBConnector();
					String output = ((DBConnectorSwing)dbConn).openNewDbDialog(
							getPanelComponent(), importInformation.getGexName()
							);
					if(output != null) {
						String outFile = FileUtils.removeExtension(output) + ".pgex";
						txtOutput.setText(outFile);
						getWizard().setNextFinishButtonEnabled(true);
					}
				} catch(Exception ex) {
					JOptionPane.showMessageDialog(
							getPanelComponent(), "The database connector is not supported"
							);
					Logger.log.error("No gex database connector", ex);
				}
			}
		}
	}

	private class TissuesPage extends WizardPanelDescriptor 
	{
		public static final String IDENTIFIER = "TISSUES_PAGE";
		private static final String gene_ID = "Gene ID";
		JTextField cutoff;

		private JList choice_list;
		private JList selected_list;

		private ArrayList<String> listOfTissues;

		public TissuesPage()
		{	
			super(IDENTIFIER);
		}
		public void aboutToDisplayPanel()
		{
			getWizard().setPageTitle ("Choose the tissues");
			Tissues tissues = new Tissues(experiment);
			listOfTissues = tissues.getTissuesList();			
			choice_list.setListData(listOfTissues.toArray());
		}
		public Object getNextPanelDescriptor()
		{
			return ImportPage.IDENTIFIER;
		}

		public Object getBackPanelDescriptor()
		{
			return FilePage.IDENTIFIER;
		}

		protected Component createContents() {			
			cutoff = new JTextField();


			/*FormLayout layout = new FormLayout (
					"pref:grow",
					"fill:[100dlu,min]:grow");*/
			FormLayout layout = new FormLayout (
					"pref, 25dlu, pref, 25dlu, pref, 25dlu, pref, 75dlu, pref",
					"p, 3dlu, p, 3dlu, p");
			PanelBuilder builder = new PanelBuilder(layout);
			builder.setDefaultDialogBorder();
			CellConstraints cc = new CellConstraints();	

			selectedTissues = new ArrayList<String>();
			listOfTissues = new ArrayList<String>(
					Arrays.asList(" "));

			//Create the list and put it in a scroll pane.
			choice_list = new JList(listOfTissues.toArray());
			choice_list.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION );
			choice_list.setSelectedIndex(0);
			choice_list.setLayoutOrientation(JList.VERTICAL);

			selected_list = new JList(selectedTissues.toArray());
			selected_list.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION );
			selected_list.setSelectedIndex(0);

			JScrollPane choiceScrollPane = new JScrollPane(choice_list);
			JScrollPane selectedScrollPane = new JScrollPane(selected_list);

			JButton add = new JButton(">>");
			add.addActionListener(new ActionListener(){
				public void actionPerformed(ActionEvent e) {
					for (  Object tissue : choice_list.getSelectedValues() ){
						if (!selectedTissues.contains(tissue)){
							selectedTissues.add((String) tissue);
							selected_list.setListData(selectedTissues.toArray());
						}
					}					
				}
			});
			JButton remove = new JButton("<<");
			remove.addActionListener(new ActionListener(){
				public void actionPerformed(ActionEvent e) {
					for(Object str : selected_list.getSelectedValues()) {
						selectedTissues.remove(str);
					}
					selected_list.setListData(selectedTissues.toArray());
				}
			});
			builder.add (choiceScrollPane, cc.xy(5,1));
			builder.add (add, cc.xy (7,1));
			builder.add (remove, cc.xy (6,1));
			builder.add (selectedScrollPane, cc.xy(8,1));
			builder.addLabel("cutoff", cc.xy (5,3));
			builder.add(cutoff, cc.xy (6,3));

			return builder.getPanel();
		}
		public void aboutToHidePanel()
		{	
			notifyObservers(listOfTissues,selectedTissues);
			String organQuery="";
			for (String organ : selectedTissues){
				organQuery += "&queryFactorValues="+organ;
			}	

			URL url = null;
			try {
				url = new URL("http://www.ebi.ac.uk/gxa/experiments/"+
						experiment+".tsv?"+
						"accessKey=&serializedFilterFactors="+
						"&queryFactorType=ORGANISM_PART" +
						"&rootContext=&heatmapMatrixSize=50"+
						"&displayLevels=false&displayGeneDistribution=false" +
						"&geneQuery=&exactMatch=true&_exactMatch=on&_geneSetMatch=on"+
						organQuery+
						"&_queryFactorValues=1" +
						"&specific=true" +
						"&_specific=on" +
						"&cutoff="+cutoff.getText());						

				String tDir = System.getProperty("java.io.tmpdir");
				File filename = File.createTempFile(tDir+"AtlasQuery", ".tmp");
				filename.deleteOnExit();


				ReadableByteChannel rbc = Channels.newChannel(url.openStream());
				FileOutputStream fos = new FileOutputStream(filename);		
				fos.getChannel().transferFrom(rbc, 0, Long.MAX_VALUE);

				DataSource ds = DataSource.getBySystemCode("En");

				importInformation.setTxtFile(filename);
				importInformation.setFirstDataRow(4);
				importInformation.setFirstHeaderRow(3);
				importInformation.guessSettings();
				importInformation.setDelimiter("\t");
				importInformation.setSyscodeFixed(true);
				importInformation.setDataSource(ds);
				importInformation.setIdColumn(0);

			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	private class ImportPage extends WizardPanelDescriptor implements ProgressListener
	{
		public static final String IDENTIFIER = "IMPORT_PAGE";

		public ImportPage()
		{
			super(IDENTIFIER);
		}

		public Object getNextPanelDescriptor()
		{
			return FINISH;
		}

		public Object getBackPanelDescriptor()
		{
			return TissuesPage.IDENTIFIER;
		}

		private JProgressBar progressSent;
		private JTextArea progressText;
		private ProgressKeeper pk;
		private JLabel lblTask;

		@Override
		public void aboutToCancel()
		{
			// let the progress keeper know that the user pressed cancel.
			pk.cancel();
		}

		protected JPanel createContents()
		{
			FormLayout layout = new FormLayout(
					"fill:[100dlu,min]:grow",
					"pref, pref, fill:pref:grow"
					);

			DefaultFormBuilder builder = new DefaultFormBuilder(layout);
			builder.setDefaultDialogBorder();

			pk = new ProgressKeeper((int)1E6);
			pk.addListener(this);
			progressSent = new JProgressBar(0, pk.getTotalWork());
			builder.append(progressSent);
			builder.nextLine();
			lblTask = new JLabel();
			builder.append(lblTask);

			progressText = new JTextArea();

			builder.append(new JScrollPane(progressText));
			return builder.getPanel();
		}

		public void setProgressValue(int i)
		{
			progressSent.setValue(i);
		}

		public void setProgressText(String msg)
		{
			progressText.setText(msg);
		}

		public void aboutToDisplayPanel()
		{
			getWizard().setPageTitle ("Perform import");
			setProgressValue(0);
			setProgressText("");

			getWizard().setNextFinishButtonEnabled(false);
			getWizard().setBackButtonEnabled(false);
		}

		public void displayingPanel()
		{
			SwingWorker<Void, Void> sw = new SwingWorker<Void, Void>() {
				@Override protected Void doInBackground() throws Exception {
					pk.setTaskName("Importing pathway");
					try
					{						
						GexTxtImporter.importFromTxt(
								importInformation,
								pk,
								standaloneEngine.getSwingEngine().getGdbManager().getCurrentGdb(),
								standaloneEngine.getGexManager()
								);
						createDefaultVisualization(importInformation);
						/*
						if (standaloneEngine.getVisualizationManager().getActiveVisualization() == null)
							System.out.println("titi");
							createDefaultVisualization(importInformation);*/
					} 
					catch (Exception e) 
					{
						Logger.log.error ("During import", e);
						setProgressValue(0);
						setProgressText("An Error Has Occurred: " + e.getMessage() + "\nSee the log for details");

						getWizard().setBackButtonEnabled(true);
					} finally {
						pk.finished();
					}
					return null;
				}

				@Override public void done()
				{
					getWizard().setNextFinishButtonEnabled(true);
					getWizard().setBackButtonEnabled(true);
				}
			};
			sw.execute();
		}

		public void progressEvent(ProgressEvent e)
		{
			switch(e.getType())
			{
			case ProgressEvent.FINISHED:
				progressSent.setValue(pk.getTotalWork());
			case ProgressEvent.TASK_NAME_CHANGED:
				lblTask.setText(pk.getTaskName());
				break;
			case ProgressEvent.REPORT:
				progressText.append(e.getProgressKeeper().getReport() + "\n");
				break;
			case ProgressEvent.PROGRESS_CHANGED:
				progressSent.setValue(pk.getProgress());
				break;
			}
		}
	}
	static double makeRoundNumber(double input)
	{
		double order = Math.pow(10, Math.round(Math.log10(input))) / 10;
		return Math.round (input / order) * order;
	}

	private void createDefaultVisualization(ImportInformation info) throws IDMapperException, DataException
	{
		VisualizationManager visMgr = standaloneEngine.getVisualizationManager(); 
		ColorSetManager csmgr = visMgr.getColorSetManager();
		ColorSet cs = new ColorSet(csmgr);
		csmgr.addColorSet(cs);

		ColorGradient gradient = new ColorGradient();
		cs.setGradient(gradient);
		
		
		//double lowerbound = makeRoundNumber (info.getMinimum() - info.getMinimum() / 10); 
		//double upperbound = makeRoundNumber (info.getMaximum() + info.getMaximum() / 10);
		double lowerbound = makeRoundNumber (0); 
		double upperbound = makeRoundNumber (250);
		
		gradient.addColorValuePair(new ColorValuePair(Color.YELLOW, lowerbound));
		gradient.addColorValuePair(new ColorValuePair(Color.BLUE, upperbound));
		
		//System.out.println(info.getMinimum()+"  "+info.getMaximum());
		//System.out.println(lowerbound+"  "+upperbound);
		
		Visualization v = new Visualization("auto-generated");

		ColorByExpression cby = new ColorByExpression(standaloneEngine.getGexManager(), 
				standaloneEngine.getVisualizationManager().getColorSetManager());
		DataInterface gex = standaloneEngine.getGexManager().getCurrentGex();
		
		Map<Integer, ? extends ISample> samplesMap = gex.getSamples();
		for(Entry<Integer, ? extends ISample> entry : samplesMap.entrySet()) {
			//Integer cle = entry.getKey();
			ISample valeur = entry.getValue();
			//System.out.println(valeur.getName());
			//System.out.println(selectedTissues);
			String tissues = valeur.getName().trim();			
			if ( selectedTissues.contains(tissues)){
				//System.out.println("found------------"+valeur.getName());
				cby.addUseSample(valeur);
			}
		}
		
		/*
		int count = Math.min (5, gex.getSamples().keySet().size());
		for (int i = 0; i < count; ++i)
		{	
			//System.out.println(gex.getSample(i).getClass());
			cby.addUseSample(gex.getSample(i));
		}*/
		cby.setSingleColorSet(cs);
		v.addMethod(cby);

		DataNodeLabel dnl = new DataNodeLabel();
		v.addMethod(dnl);
		
		visMgr.removeVisualization(v);
		visMgr.addVisualization(v);
		visMgr.setActiveVisualization(v);
	}

	@Override
	public void addObserver(ObserverSidePanel obs) {
		// TODO Auto-generated method stub
		observers.add(obs);
	}

	@Override
	public void notifyObservers(ArrayList<String> progress,ArrayList<String> selected) {
		// TODO Auto-generated method stub
		for (ObserverSidePanel obs : observers){
			obs.update(progress,selected);
		}
	}

	@Override
	public void delOneObserver(ObserverSidePanel obs) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void delAllObservers() {
		// TODO Auto-generated method stub
		
	}
}