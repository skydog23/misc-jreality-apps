package dragonfly.dynamen.attic;

import static de.jreality.shader.CommonAttributes.DIFFUSE_COLOR;
import static de.jreality.shader.CommonAttributes.EDGE_DRAW;
import static de.jreality.shader.CommonAttributes.LINE_SHADER;
import static de.jreality.shader.CommonAttributes.TEXT_OFFSET;
import static de.jreality.shader.CommonAttributes.POLYGON_SHADER;
import static de.jreality.shader.CommonAttributes.TEXT_SCALE;
import static de.jreality.shader.CommonAttributes.TEXT_SHADER;
import static de.jreality.shader.CommonAttributes.TUBE_RADIUS;
import static de.jreality.shader.CommonAttributes.VERTEX_DRAW;

import java.awt.Color;

import charlesgunn.jreality.geometry.projective.PointRangeFactory;
import charlesgunn.jreality.texture.SimpleTextureFactory;
import charlesgunn.jreality.texture.SimpleTextureFactory.TextureType;
import de.jreality.geometry.Primitives;
import de.jreality.math.Matrix;
import de.jreality.math.MatrixBuilder;
import de.jreality.math.Rn;
import de.jreality.scene.Appearance;
import de.jreality.scene.IndexedLineSet;
import de.jreality.scene.SceneGraphComponent;
import de.jreality.scene.data.Attribute;
import de.jreality.scene.data.AttributeEntityUtility;
import de.jreality.scene.data.StorageModel;
import de.jreality.shader.Texture2D;
import de.jreality.util.SceneGraphUtility;
import de.jtem.numericalMethods.calculus.odeSolving.ODE;

public class RigidBodyODEE2 extends AbstractRigidBodyODE2D {

	double moment = 3.0;
	
	SceneGraphComponent velocityBody, momentumBody;
	IndexedLineSet avb = null, amb = null;
	
	PointRangeFactory momentumFactory, velocityFactory;	
	
	@Override
	protected void doOneStep() {
		extrap.odex(ode, solution, time,time+deltaT);	
		double angle = solution[0];
		double tx = solution[1];
		double ty = solution[2];
		Rn.setIdentityMatrix(motion);
		double c = Math.cos(angle);
		double s = Math.sin(angle);
		motion[0] = motion[5] = c;
		motion[1] = -s;
		motion[4] = s;
		motion[3] = tx;
		motion[7] = ty;
		parent.getTransformation().setMatrix(motion);
		System.arraycopy(solution, 3, velocity, 0, 3);
		setVelocity(velocity);
		update();
	}
	@Override
	protected ODE getODE()	{
		if (ode == null) ode = new ODE() {
			// the rigid body motion is determined by two  equations
			// g' = g v			matrix multiplication
			// v' = A v			Euler equations for velocity
			public void eval(double t, double[] x, double[] y) {
				// y = {angle, tx, ty, a, b, c}
				double c = Math.cos(x[0]);
				double s = Math.sin(x[0]);
				y[0] = x[5];		// angular velocity
				y[1] = c*x[4] + s*x[3];
				y[2] = s*x[4] - c*x[3];
				
				y[3] = x[4]*x[5];	// change of x-coord of null point
				y[4] = -x[3]*x[5];	// change of y-coord of null point
				y[5] = 0;	// angular velocity is constant
			}
	
			public int getNumberOfEquations() {
				return 6;
			}
			
		};
		return ode;
	}
	@Override
	protected void updateVelocity() {
		super.updateVelocity();
		System.arraycopy(velocity, 0, solution, 3, 3);
	}
	
	@Override
	public void resetMotion() {
		motion = Rn.identityMatrix(4);
		parent.getTransformation().setMatrix(motion);
		time = 0;
		solution[0] = solution[1] = solution[2] = 0.0;
		updateVelocity();
		updateSceneGraphRepn();
//		doOneStep();
	}
	@Override
	protected void updateSceneGraphRepn() {
		if (velocity[2] != 0)	{
			velocityBody.setVisible(true);
			double f= 1.0/velocity[2];
			double[] pt1 = {velocity[0]*f, velocity[1]*f, 0, 1.0};
			double[] ptm = {velocity[0]*f, velocity[1]*f, 1, 1.0};
			double[] pt2 = {velocity[0]*f, velocity[1]*f, 2.0, 1.0};
			velocityFactory.setElement0(pt1);
			velocityFactory.setElement1(pt2);
			velocityFactory.setCenter(ptm);
			velocityFactory.update();
			if (avb == null) velocityFactory.getLine().setEdgeAttributes(Attribute.LABELS, StorageModel.STRING_ARRAY.createReadOnly(
					new String[]{"velocity state"}));
		} else velocityBody.setVisible(false);
		if (Rn.innerProduct(momentum, momentum, 2) > 10E-8) {
			momentumBody.setVisible(true);
			double[] pt1 = {momentum[1], -momentum[0], 0, 0};
			double[] pt2 = {0, momentum[2], 0, -momentum[1]};
			momentumFactory.setElement0(pt1);
			momentumFactory.setElement1(pt2);
			momentumFactory.update();
			if (amb == null) momentumFactory.getLine().setEdgeAttributes(Attribute.LABELS, StorageModel.STRING_ARRAY.createReadOnly(
					new String[]{"momentum state"}));
		} else momentumBody.setVisible(false);
	}

	@Override
	protected void createSceneGraphRepn() {
		parent = SceneGraphUtility.createFullSceneGraphComponent("body");
		Appearance ap = parent.getAppearance();
		ap.setAttribute(POLYGON_SHADER+"."+DIFFUSE_COLOR, Color.white);
		object = SceneGraphUtility.createFullSceneGraphComponent("disk");
		object.getAppearance().setAttribute(VERTEX_DRAW, true);
		object.getAppearance().setAttribute(EDGE_DRAW, false);
		object.setGeometry(Primitives.texturedQuadrilateral(null)); //regularPolygon(30));
		MatrixBuilder.euclidean().translate(-1,-1,0).scale(2).assignTo(object);
		ap = object.getAppearance();
		Texture2D tex2d = (Texture2D) AttributeEntityUtility
		   .createAttributeEntity(Texture2D.class, "polygonShader.texture2d", ap, true);		
		SimpleTextureFactory stf = new SimpleTextureFactory();
		stf.setType(TextureType.CHECKERBOARD);
		stf.setSize(16);
		stf.setColor(0, Color.red);
		stf.setColor(1, Color.blue);
		stf.update();
		tex2d.setImage(stf.getImageData());
		Matrix foo = new Matrix();
		MatrixBuilder.euclidean().scale(8,8,1).assignTo(foo);
		tex2d.setTextureMatrix(foo);
		//ellipsoid.setGeometry(new Sphere());
		parent.addChild(object);
		velocityBody = SceneGraphUtility.createFullSceneGraphComponent("velBody");
		velocityBody.getAppearance().setAttribute(LINE_SHADER+"."+TEXT_SHADER+"."+DIFFUSE_COLOR, new Color(0, 255,0));
		velocityBody.getAppearance().setAttribute(LINE_SHADER+"."+TEXT_SHADER+"."+TEXT_OFFSET, new double[]{.1,0,0.0});
//		velocityBody.getAppearance().setAttribute(LINE_SHADER+"."+TEXT_SHADER+"."+SCALE, .006);
		velocityBody.getAppearance().setAttribute(EDGE_DRAW, true);
		velocityBody.getAppearance().setAttribute(LINE_SHADER+"."+TUBE_RADIUS, .02);
		velocityBody.getAppearance().setAttribute(LINE_SHADER+"."+POLYGON_SHADER+"."+DIFFUSE_COLOR, new Color(0, 255,0));
		velocityFactory = new PointRangeFactory();
		velocityFactory.setFiniteSphere(true);
		velocityFactory.setSphereRadius(2.0);

		momentumBody = SceneGraphUtility.createFullSceneGraphComponent("momBody");
		momentumBody.getAppearance().setAttribute(LINE_SHADER+"."+TEXT_SHADER+"."+DIFFUSE_COLOR, new Color(250, 0, 250));
		momentumBody.getAppearance().setAttribute(LINE_SHADER+"."+TEXT_SHADER+"."+TEXT_OFFSET, new double[]{2,0.2,2.2});
		momentumBody.getAppearance().setAttribute(LINE_SHADER+"."+TEXT_SHADER+"."+TEXT_SCALE, .008);
		momentumBody.getAppearance().setAttribute(EDGE_DRAW, true);
		momentumBody.getAppearance().setAttribute(LINE_SHADER+"."+TUBE_RADIUS, .02);
		momentumBody.getAppearance().setAttribute(LINE_SHADER+"."+POLYGON_SHADER+"."+DIFFUSE_COLOR, new Color(250, 0, 250));
		momentumFactory = new PointRangeFactory();
		momentumFactory.setFiniteSphere(true);
		momentumFactory.setSphereRadius(10.0);

		updateSceneGraphRepn();
		avb = velocityFactory.getLine();			
		velocityBody.setGeometry(avb);
		parent.addChild(velocityBody);

		amb = momentumFactory.getLine();			
		momentumBody.setGeometry(amb);
		parent.addChild(momentumBody);
	}
	
	// convenience methods for the euclidean case
	public double getMoment()	{
		return moment;
	}
	
	public void setMoment(double d)	{
		moment = d;
		setMoments(new double[]{1,1,d});
	}
	@Override
	double[] getInitialSolution()	{
		return new double[6];
	}


}
