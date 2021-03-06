package de.tomsplayground.peanuts.client.editors.forecast;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jface.dialogs.ErrorDialog;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IEditorSite;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.part.MultiPageEditorPart;


public class ForecastEditor extends MultiPageEditorPart {

	public static final String ID = "de.tomsplayground.peanuts.client.forecastEditor";

	private MetaEditorPart metaEditorPart;

	@Override
	public void init(IEditorSite site, IEditorInput input) throws PartInitException {
		if ( !(input instanceof ForecastEditorInput)) {
			throw new PartInitException("Invalid Input: Must be ForecastEditorInput");
		}
		super.init(site, input);
		setPartName(input.getName());
	}

	@Override
	protected void createPages() {
		metaEditorPart = new MetaEditorPart();
		createEditorPage(metaEditorPart, "Meta Data");
	}

	private void createEditorPage(IEditorPart editor, String name) {
		try {
			int pageIndex = addPage(editor, getEditorInput());
			setPageText(pageIndex, name);
		} catch (PartInitException e) {
			ErrorDialog.openError(getSite().getShell(), "Error creating nested editor", null,
				e.getStatus());
		}
	}

	@Override
	public void doSave(IProgressMonitor monitor) {
		metaEditorPart.doSave(monitor);
		firePropertyChange(IEditorPart.PROP_DIRTY);
	}

	@Override
	public void doSaveAs() {
		// nothing to do
	}

	@Override
	public boolean isSaveAsAllowed() {
		return false;
	}

}
