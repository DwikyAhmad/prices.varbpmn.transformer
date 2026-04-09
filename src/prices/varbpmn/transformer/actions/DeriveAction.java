package prices.varbpmn.transformer.actions;

import java.io.File;
import java.io.FileInputStream;
import java.net.URL;
import java.util.Collections;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.FileLocator;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Platform;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.IObjectActionDelegate;
import org.eclipse.ui.IWorkbenchPart;
import org.osgi.framework.Bundle;

// Import untuk EMF dan ATL
import org.eclipse.m2m.atl.core.IExtractor;
import org.eclipse.m2m.atl.core.IInjector;
import org.eclipse.m2m.atl.core.launch.ILauncher;
import org.eclipse.m2m.atl.core.IModel;
import org.eclipse.m2m.atl.core.IReferenceModel;
import org.eclipse.m2m.atl.core.ModelFactory;
import org.eclipse.m2m.atl.core.emf.EMFExtractor;
import org.eclipse.m2m.atl.core.emf.EMFInjector;
import org.eclipse.m2m.atl.core.emf.EMFModelFactory;
import org.eclipse.m2m.atl.engine.emfvm.launch.EMFVMLauncher;

public class DeriveAction implements IObjectActionDelegate {

	private IFile selectedFile;
	private Shell shell;

	@Override
	public void setActivePart(IAction action, IWorkbenchPart targetPart) {
		// Mengambil "layar" aktif saat ini untuk tempat memunculkan pop-up
		this.shell = targetPart.getSite().getShell();
	}

	@Override
	public void selectionChanged(IAction action, ISelection selection) {
		// Logika untuk mengekstrak file yang sedang di-klik kanan
		if (selection instanceof IStructuredSelection) {
			Object firstElement = ((IStructuredSelection) selection).getFirstElement();
			if (firstElement instanceof IFile) {
				this.selectedFile = (IFile) firstElement;
			}
		}
	}

	@Override
	public void run(IAction action) {
		if (selectedFile != null) {
			try {
				// 1. Dapatkan path file input (.bpmn2) yang di-klik user
				String inputFilePath = selectedFile.getLocationURI().toString();
				
				// 2. Tentukan nama file output
				String outputFileName = selectedFile.getName().replace(".bpmn2", "_derived.bpmn2");
				String outputFilePath = "file:/" + selectedFile.getParent().getLocation().append(outputFileName).toOSString().replace('\\', '/');

				// 3. Cari lokasi file .asm di dalam plugin ini
				Bundle bundle = Platform.getBundle("prices.varbpmn.transformer");
				URL asmURL = bundle.getEntry("transformation/DeriveVBPMN.asm");
				
				if (asmURL == null) {
				    throw new Exception("File DeriveVBPMN.asm tidak ditemukan di dalam folder transformation/");
				}
				
				URL resolvedAsmURL = FileLocator.resolve(asmURL);
				String asmPath = resolvedAsmURL.getPath();

				// 4. Persiapkan Factory & Injector EMF/ATL
				ModelFactory factory = new EMFModelFactory();
				IInjector injector = new EMFInjector();
				IExtractor extractor = new EMFExtractor();

				// 5. Load Metamodel (Pastikan URI ini cocok dengan yang ada di script ATL Anda)
				IReferenceModel bpmnMetamodel = factory.newReferenceModel();
				injector.inject(bpmnMetamodel, "http://www.omg.org/spec/BPMN/20100524/MODEL"); 
				
				// 6. Load Model Input (VBPMN)
				IModel inModel = factory.newModel(bpmnMetamodel);
				injector.inject(inModel, inputFilePath);

				// 7. Siapkan Model Output kosong
				IModel outModel = factory.newModel(bpmnMetamodel);

				// 8. Inisialisasi dan Jalankan Launcher ATL
				EMFVMLauncher launcher = new EMFVMLauncher();
				launcher.initialize(Collections.<String, Object>emptyMap());
				
				// Mendaftarkan model. Nama IN, OUT, dan BPMN harus persis dengan header script .atl
				launcher.addInModel(inModel, "IN", "BPMN");
				launcher.addOutModel(outModel, "OUT", "VBPMN");

				// Eksekusi transformasi
				launcher.launch(ILauncher.RUN_MODE, 
								new NullProgressMonitor(), 
								Collections.<String, Object>emptyMap(),
								new FileInputStream(new File(asmPath)));

				// 9. Extract model output menjadi file fisik .bpmn2
				extractor.extract(outModel, outputFilePath);

				// 10. Refresh Eclipse Workspace agar file baru langsung muncul di layar
				selectedFile.getParent().refreshLocal(IResource.DEPTH_ONE, null);

				// Notifikasi Sukses
				MessageDialog.openInformation(shell, "Transformasi Berhasil", 
				    "Derivasi VBPMN sukses dieksekusi!\n\nFile hasil transformasi tersimpan sebagai:\n" + outputFileName);

			} catch (Exception e) {
				e.printStackTrace();
				MessageDialog.openError(shell, "Gagal Melakukan Transformasi", 
				    "Terjadi kesalahan saat mengeksekusi mesin ATL:\n" + e.getMessage() + 
				    "\n\nSilakan cek Error Log untuk detail lebih lanjut.");
			}
		} else {
			MessageDialog.openError(shell, "Error", "Tidak ada file yang dipilih.");
		}
	}
}