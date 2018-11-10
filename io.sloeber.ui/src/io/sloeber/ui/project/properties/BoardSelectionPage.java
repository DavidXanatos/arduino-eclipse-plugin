package io.sloeber.ui.project.properties;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

import org.eclipse.cdt.core.parser.util.ArrayUtil;
import org.eclipse.cdt.core.settings.model.ICConfigurationDescription;
import org.eclipse.cdt.core.settings.model.ICResourceDescription;
import org.eclipse.cdt.ui.newui.AbstractCPropertyTab;
import org.eclipse.cdt.ui.newui.AbstractPage;
import org.eclipse.cdt.ui.newui.ICPropertyProvider;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.Status;
import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Text;

import io.sloeber.core.api.BoardDescriptor;
import io.sloeber.core.api.Defaults;
import io.sloeber.core.api.PackageManager;
import io.sloeber.core.api.PasswordManager;
import io.sloeber.core.api.SerialManager;
import io.sloeber.ui.Activator;
import io.sloeber.ui.LabelCombo;
import io.sloeber.ui.Messages;

/**
 * The ArduinoSelectionPage class is used in the new wizard and the project
 * properties. This class controls the gui and the data underneath the gui. This
 * class allows to select the arduino board and the port name
 *
 * @author Jan Baeyens
 * @see ArduinoProperties ArduinoSettingsPage
 *
 */

public class BoardSelectionPage extends AbstractCPropertyTab {
	private static final String TRUE = "TRUE"; //$NON-NLS-1$
	private static final String FALSE = "FALSE"; //$NON-NLS-1$

	public Text myFeedbackControl;
	protected LabelCombo myControlBoardsTxtFile;
	protected LabelCombo mycontrolBoardName;
	protected LabelCombo myControlUploadProtocol;
	protected LabelCombo myControlUploadPort;
	protected LabelCombo[] myBoardOptionCombos = null;
	protected Listener myBoardSelectionChangedListener = null;
	protected BoardDescriptor myBoardID = null;

	private TreeMap<String, String> myAllBoardsFiles = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
	private org.eclipse.swt.widgets.Button myPasswordButton;

	private Listener myBoardFileModifyListener = new Listener() {
		@Override
		public void handleEvent(Event e) {

			File boardFile = getSelectedBoardsFile();
			myBoardID.setreferencingBoardsFile(boardFile);

			/*
			 * Change the list of available boards
			 */
			String CurrentBoard = getBoardName();
			mycontrolBoardName.setItems(myBoardID.getCompatibleBoards());
			mycontrolBoardName.setText(CurrentBoard);

			/*
			 * Change the list of available upload protocols
			 */
			String CurrentUploadProtocol = getUpLoadProtocol();
			myControlUploadProtocol.setItems(myBoardID.getUploadProtocols());
			myControlUploadProtocol.setText(CurrentUploadProtocol);

			if (myControlUploadProtocol.getText().isEmpty()) {
				myBoardID.setUploadProtocol(Defaults.getDefaultUploadProtocol());
				myControlUploadProtocol.setText(Defaults.getDefaultUploadProtocol());
			}

			myBoardModifyListener.handleEvent(null);
		}

	};

	protected Listener myBoardModifyListener = new Listener() {
		@Override
		public void handleEvent(Event e) {

			myBoardID.setBoardName(getBoardName());

			for (LabelCombo curLabelCombo : myBoardOptionCombos) {
				curLabelCombo.setItems(myBoardID.getMenuItemNamesFromMenuID(curLabelCombo.getID()));
				curLabelCombo.setLabel(myBoardID.getMenuNameFromMenuID(curLabelCombo.getID()));
			}

			isPageComplete();
			enableControls();
		}
	};
	protected Listener myLabelComboListener = new Listener() {
		@Override
		public void handleEvent(Event e) {
			isPageComplete();
		}
	};
	private Composite myParameterComposite;

	/**
	 * Get the configuration we are currently working in. The configuration is null
	 * if we are in the create sketch wizard.
	 *
	 * @return the configuration to save info into
	 */
	public ICConfigurationDescription getConfdesc() {
		if (page != null) {
			return getResDesc().getConfiguration();
		}
		return null;
	}

	@Override
	public void createControls(Composite parent, ICPropertyProvider provider) {
		super.createControls(parent, provider);
		draw(parent);

	}

	public void setListener(Listener BoardSelectionChangedListener) {
		myBoardSelectionChangedListener = BoardSelectionChangedListener;
	}

	private void createLine(Composite composite) {
		Label line = new Label(composite, SWT.SEPARATOR | SWT.HORIZONTAL | SWT.BOLD);
		GridData gridData = new GridData(GridData.FILL_HORIZONTAL);
		gridData.horizontalSpan = 3;
		line.setLayoutData(gridData);
	}

	public void draw(Composite parent) {
		Composite mainComp = parent;
		if (usercomp != null) {
			mainComp = usercomp;
		}
		GridLayout rowLayout =new GridLayout();
		rowLayout.numColumns = 1;
		mainComp.setLayout(rowLayout);
		
		if (myBoardID == null) {
			myBoardID = BoardDescriptor.makeBoardDescriptor(getConfdesc());
			if (myBoardID.getActualCoreCodePath() == null) {
				Activator.log(
						new Status(IStatus.ERROR, Activator.getId(), Messages.BoardSelectionPage_failed_to_find_platform
								.replace(Messages.PLATFORM, myBoardID.getReferencingPlatformFile().toString())));
			}
		}

		String[] allBoardsFileNames = PackageManager.getAllBoardsFiles();
		for (String curBoardFile : allBoardsFileNames) {
			myAllBoardsFiles.put(tidyUpLength(curBoardFile), curBoardFile);
		}
		if (myAllBoardsFiles.isEmpty()) {
			Activator.log(new Status(IStatus.ERROR, Activator.getId(), Messages.error_no_platform_files_found, null));
		}
		
		/*We know all is well start drawing*/
		Composite boardComposite = new Composite (mainComp,SWT.NONE);
		GridLayout theGridLayout = new GridLayout();
		theGridLayout.numColumns = 3;
		boardComposite.setLayout(theGridLayout);

		myControlBoardsTxtFile = new LabelCombo(boardComposite, Messages.BoardSelectionPage_platform_folder, null, 2,
				true);
		myControlBoardsTxtFile.setItems(myAllBoardsFiles.keySet().toArray(new String[0]));
		createLine(boardComposite);

		mycontrolBoardName = new LabelCombo(boardComposite, Messages.BoardSelectionPage_board, null, 2, true);
		mycontrolBoardName.setItems(myAllBoardsFiles.keySet().toArray(new String[0]));

		myControlUploadProtocol = new LabelCombo(boardComposite, Messages.BoardSelectionPage_upload_protocol, null, 2,
				true);
		myControlUploadProtocol.setItems(myAllBoardsFiles.keySet().toArray(new String[0]));

		// ----
		myControlUploadPort = new LabelCombo(boardComposite, Messages.ui_port, null, 1, false);

		myControlUploadPort.setItems(ArrayUtil.addAll(SerialManager.listNetworkPorts(), SerialManager.listComPorts()));
		myPasswordButton = new org.eclipse.swt.widgets.Button(boardComposite, SWT.PUSH | SWT.CENTER);
		myPasswordButton.setText(Messages.set_or_remove_password);
		myPasswordButton.addListener(SWT.Selection, new Listener() {
			@Override
			public void handleEvent(Event e) {
				switch (e.type) {
				case SWT.Selection:
					String host = getUpLoadPort().split(" ")[0]; //$NON-NLS-1$
					if (host.equals(getUpLoadPort())) {
						Activator.log(
								new Status(IStatus.ERROR, Activator.getId(), Messages.port_is_not_a_computer_name));
					} else {
						PasswordManager passwordManager = new PasswordManager();
						PasswordDialog dialog = new PasswordDialog(boardComposite.getShell());
						passwordManager.setHost(host);
						dialog.setPasswordManager(passwordManager);
						dialog.open();
					}
					break;
				}
			}
		});
		createLine(boardComposite);

		TreeMap<String, String> menus = PackageManager.getAllmenus();

		myBoardOptionCombos = new LabelCombo[menus.size()];
		myParameterComposite = new Composite(mainComp,  SWT.V_SCROLL);
		GridLayout theParamLayout = new GridLayout(2,false);
		myParameterComposite.setLayout(theParamLayout);
		int index = 0;
		for (Map.Entry<String, String> curMenu : menus.entrySet()) {
			myBoardOptionCombos[index] = new LabelCombo(myParameterComposite, curMenu.getValue(), curMenu.getKey(), 1, true);
			myBoardOptionCombos[index++].addListener(myLabelComboListener);

		}

		// Create the control to alert parents of changes
		myFeedbackControl = new Text(mainComp, SWT.None);
		myFeedbackControl.setVisible(false);
		myFeedbackControl.setEnabled(false);
		GridData theGriddata;
		theGriddata = new GridData();
		theGriddata.horizontalSpan = 0;
		myFeedbackControl.setLayoutData(theGriddata);
		// End of special controls

		setValues();

		mycontrolBoardName.addListener(SWT.Modify, myBoardModifyListener);
		myControlBoardsTxtFile.addListener(SWT.Modify, myBoardFileModifyListener);

		//enableControls();
		Dialog.applyDialogFont(mainComp);
	}

	private static String tidyUpLength(String longName) {
		IPath longPath = new Path(longName).removeLastSegments(1);
		IPath tidyPath = longPath;
		int segments = longPath.segmentCount();
		if (segments > 7) {
			tidyPath = longPath.removeLastSegments(segments - 2);
			tidyPath = tidyPath.append("..."); //$NON-NLS-1$
			tidyPath = tidyPath.append(longPath.removeFirstSegments(segments - 4));
		}
		return tidyPath.toString();
	}

	public boolean isPageComplete() {

		boolean MenuOpionsValidAndComplete = true;
		boolean ret = true;
		int selectedBoardFile = myControlBoardsTxtFile.getSelectionIndex();
		if (selectedBoardFile == -1)
			return false;

		for (LabelCombo curLabelCombo : myBoardOptionCombos) {
			MenuOpionsValidAndComplete = MenuOpionsValidAndComplete && curLabelCombo.isValid();
		}

		ret = !getBoardName().isEmpty() && MenuOpionsValidAndComplete;
		if (!myFeedbackControl.getText().equals(ret ? TRUE : FALSE)) {
			myFeedbackControl.setText(ret ? TRUE : FALSE);
		}
		if (ret) {
			if (myBoardSelectionChangedListener != null) {
				myBoardSelectionChangedListener.handleEvent(new Event());
			}
		}

		return ret;
	}

	protected void enableControls() {
		for (LabelCombo curLabelCombo : myBoardOptionCombos) {
			curLabelCombo.setVisibility();
		}
		if (usercomp != null) {
			usercomp.layout();
		}
		myParameterComposite.layout();
		//myParameterComposite.pack();
		myParameterComposite.redraw();
	}

	@Override
	public boolean canBeVisible() {
		return true;
	}

	@Override
	protected void performDefaults() {
		// nothing to do here

	}

	@Override
	protected void updateData(ICResourceDescription cfg) {
		myBoardID.saveConfiguration();
		myBoardID = BoardDescriptor.makeBoardDescriptor(cfg.getConfiguration());
		setValues();
	}

	@Override
	protected void updateButtons() {
		// nothing to do here

	}

	private void setValues() {

		myControlBoardsTxtFile.setText(tidyUpLength(myBoardID.getReferencingBoardsFile().toString()));
		mycontrolBoardName.setItems(myBoardID.getCompatibleBoards());
		mycontrolBoardName.setText(myBoardID.getBoardName());

		String CurrentUploadProtocol = getUpLoadProtocol();
		myControlUploadProtocol.setItems(myBoardID.getUploadProtocols());
		myControlUploadProtocol.setText(CurrentUploadProtocol);
		if (getUpLoadProtocol().isEmpty()) {
			myControlUploadProtocol.setText(myBoardID.getProgrammer());
			if (myControlUploadProtocol.getText().isEmpty()) {
				myControlUploadProtocol.setText(Defaults.getDefaultUploadProtocol());
			}
		}

		myControlUploadPort.setValue(myBoardID.getUploadPort());

		// set the options in the combo boxes before setting the value
		Map<String, String> options = myBoardID.getOptions();

		for (LabelCombo curLabelCombo : myBoardOptionCombos) {
			curLabelCombo.setItems(myBoardID.getMenuItemNamesFromMenuID(curLabelCombo.getID()));
			if (options != null) {
				String value = options.get(curLabelCombo.getID());
				if (value != null) {
					try {
						curLabelCombo.setValue(myBoardID.getMenuItemNamedFromMenuItemID(value, curLabelCombo.getID()));
					} catch (Exception e) {
						// When this fails no default value will be set
						// so nothing to do here
					}
				}
			}
		}
	}

	@Override
	protected void performOK() {
		doOK();
		super.performOK();
	}

	@Override
	protected void performApply(ICResourceDescription src, ICResourceDescription dst) {
		doOK();
	}

	private void doOK() {
		myBoardID.setreferencingBoardsFile(getSelectedBoardsFile());
		myBoardID.setUploadPort(getUpLoadPort());
		myBoardID.setUploadProtocol(getUpLoadProtocol());
		myBoardID.setBoardName(getBoardName());
		myBoardID.setOptions(getOptions());
		ICConfigurationDescription confdesc = getConfdesc();

		try {
			myBoardID.save(confdesc);

		} catch (Exception e) {
			Activator.log(new Status(IStatus.ERROR, Activator.getId(), Messages.error_adding_arduino_code, e));
		}
	}

	private class ArduinoSelectionPageListener implements Listener {
		private AbstractPage myPage;

		ArduinoSelectionPageListener(AbstractPage page) {
			myPage = page;
		}

		@Override
		public void handleEvent(Event event) {
			myPage.setValid(isPageComplete());
		}
	}

	@Override
	public void handleTabEvent(int kind, Object data) {
		if (kind == 222) {
			myFeedbackControl.addListener(SWT.Modify, new ArduinoSelectionPageListener((AbstractPage) data));
		}
		super.handleTabEvent(kind, data);
	}

	protected File getSelectedBoardsFile() {
		if (myControlBoardsTxtFile == null) {
			return null;
		}
		String selectedText = myControlBoardsTxtFile.getText().trim();
		String longText = myAllBoardsFiles.get(selectedText);
		if (longText == null) {
			return null;// this should not happen
		}
		return new File(longText);
	}

	public String getUpLoadPort() {
		if (myControlUploadPort == null) {
			return new String();
		}
		return myControlUploadPort.getValue();
	}

	protected String getBoardName() {
		if (mycontrolBoardName == null) {
			return null;
		}
		return mycontrolBoardName.getText().trim();
	}

	protected String getUpLoadProtocol() {
		if (myControlUploadProtocol == null) {
			return Defaults.getDefaultUploadProtocol();
		}
		return myControlUploadProtocol.getText().trim();
	}

	private Map<String, String> getOptions() {
		if (myBoardOptionCombos == null) {
			return null;
		}
		Map<String, String> options = new HashMap<>();
		for (LabelCombo curLabelCombo : myBoardOptionCombos) {
			if (curLabelCombo.isVisible()) {
				String itemID = curLabelCombo.getID();
				String itemValue = curLabelCombo.getValue();
				String menuItemID = myBoardID.getMenuItemIDFromMenuItemName(itemValue, itemID);
				options.put(itemID, menuItemID);
			}
		}
		return options;
	}

	public BoardDescriptor getBoardID() {
		if (myBoardID == null) {
			myBoardID = BoardDescriptor.makeBoardDescriptor(getConfdesc());
		}
		// only update the values if the page has been drawn
		if (myBoardOptionCombos != null) {
			myBoardID.setreferencingBoardsFile(getSelectedBoardsFile());
			myBoardID.setBoardName(getBoardName());
			myBoardID.setOptions(getOptions());
			myBoardID.setUploadPort(getUpLoadPort());
			myBoardID.setUploadProtocol(getUpLoadProtocol());
		}
		return myBoardID;
	}

}
