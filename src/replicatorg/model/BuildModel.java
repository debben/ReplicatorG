package replicatorg.model;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.util.LinkedList;
import java.util.Queue;
import java.util.logging.Level;

import javax.media.j3d.Shape3D;
import javax.media.j3d.Transform3D;

import org.j3d.renderer.java3d.loaders.STLLoader;

import replicatorg.app.Base;

import com.sun.j3d.loaders.Scene;

public class BuildModel extends BuildElement {

	private File file;
	private Transform3D transform = new Transform3D();
	private Shape3D shape = null;
	
	BuildModel(Build build, File file) {
		this.file = file;
	}		

	public BuildElement.Type getType() {
		return BuildElement.Type.MODEL;
	}

	public String getPath() {
		try {
			return file.getCanonicalPath();
		} catch (IOException ioe) { return null; }
	}

	public Shape3D getShape() {
		if (shape == null) { 
			loadShape();
		}
		return shape;
	}
	
	private void loadShape() {
		STLLoader loader = new STLLoader();
		Scene scene = null;
		try {
			scene = loader.load(file.getCanonicalPath());
		} catch (Exception e) {
			Base.logger.log(Level.SEVERE,"Error loading model "+file.getPath(),e);
		}
		if (scene == null) { return; }
		shape = (Shape3D)scene.getSceneGroup().getChild(0);
	}

	public Transform3D getTransform() { return transform; }
	
	class UndoEntry {
		public Transform3D transform;
		public String description;
		public UndoEntry(Transform3D transform, String description) {
			this.transform = transform;
			this.description = description;
		}
	}
	
	protected Queue<UndoEntry> undoQueue = new LinkedList<UndoEntry>();
	
	public void setTransform(Transform3D t, String description) {
		if (transform.equals(t)) return;
		transform.set(t);
		undoQueue.add(new UndoEntry(t,description));
		setModified(true);
	}

	@Override
	void writeToStream(OutputStream ostream) {
		// TODO Auto-generated method stub
		
	}
}
