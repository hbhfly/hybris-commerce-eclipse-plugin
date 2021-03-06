package com.hybris.hyeclipse.ytypesystem;

import java.io.*;
import java.lang.reflect.Field;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.*;
import java.util.Map.Entry;

import org.apache.commons.lang.StringUtils;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.FileLocator;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.preferences.InstanceScope;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.ui.plugin.AbstractUIPlugin;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleException;
import org.osgi.service.prefs.Preferences;

import com.hybris.yps.hyeclipse.ExtensionHolder;

import de.hybris.bootstrap.config.ExtensionInfo;
import de.hybris.bootstrap.config.PlatformConfig;
import de.hybris.bootstrap.config.SystemConfig;
import de.hybris.bootstrap.typesystem.YAttributeDescriptor;
import de.hybris.bootstrap.typesystem.YType;
import de.hybris.bootstrap.typesystem.YTypeSystem;

/**
 * The activator class controls the plug-in life cycle
 */
public class Activator extends AbstractUIPlugin {

	// The plug-in ID
	public static final String PLUGIN_ID = "com.hybris.hyeclipse.ytypesystem"; //$NON-NLS-1$

	public static final String YBOOTSTRAP_PLUGIN_ID = "com.hybris.hyeclipse.ybootstrap";
	
	// The shared instance
	private static Activator plugin;
	
	private Bundle ybootstrapBundle;
	private File platformHome;
	private SystemConfig systemConfig;
	private PlatformConfig platformConfig;
	private YTypeSystem typeSystem;
	private Set<? extends YType> allTypes;
	private List<String> allTypeNames;
	
	/**
	 * The constructor
	 */
	public Activator() {}

	/*
	 * (non-Javadoc)
	 * @see org.eclipse.ui.plugin.AbstractUIPlugin#start(org.osgi.framework.BundleContext)
	 */
	public void start(BundleContext bundleContext) throws Exception {
		super.start(bundleContext);
		plugin = this;
		
		if (getPlatformHome() != null) {
			loadBootstrapBundle(bundleContext);
		}
	}

	/*
	 * (non-Javadoc)
	 * @see org.eclipse.ui.plugin.AbstractUIPlugin#stop(org.osgi.framework.BundleContext)
	 */
	public void stop(BundleContext bundleContext) throws Exception {
		plugin = null;
		super.stop(bundleContext);
	}

	/**
	 * Returns the shared instance
	 *
	 * @return the shared instance
	 */
	public static Activator getDefault() {
		return plugin;
	}

	/**
	 * Returns an image descriptor for the image file at the given
	 * plug-in relative path
	 *
	 * @param path the path
	 * @return the image descriptor
	 */
	public static ImageDescriptor getImageDescriptor(String path) {
		return imageDescriptorFromPlugin(PLUGIN_ID, path);
	}
	
	public Bundle getYbootstrapBundle() {
		return ybootstrapBundle;
	}

	public void setYbootstrapBundle(Bundle ybootstrapBundle) {
		this.ybootstrapBundle = ybootstrapBundle;
	}
	
	public File getPlatformHome() {
		if (platformHome == null) {
			
			//Get platform home from workspace preferences
			Preferences preferences = InstanceScope.INSTANCE.getNode("com.hybris.hyeclipse.preferences");
			String platformHomeStr = preferences.get("platform_home", null);
			if (platformHomeStr == null) {
				IProject platformProject = ResourcesPlugin.getWorkspace().getRoot().getProject("platform");
				IPath platformProjectPath = platformProject.getLocation();
				if (platformProjectPath != null) {
					setPlatformHome(platformProjectPath.toFile());
				}
			}
			else {
				setPlatformHome(new File(platformHomeStr));
			}
		}
		return platformHome;
	}

	public void setPlatformHome(File platformHome) {
		this.platformHome = platformHome;
	}
	
	public void unloadBootstrapBundle() {
		if (ybootstrapBundle != null) {
			try {
				ybootstrapBundle.uninstall();
				ybootstrapBundle = null;
			}
			catch (BundleException e) {
				e.printStackTrace();
			}
		}
	}
	
	public void loadBootstrapBundle(BundleContext context) {
		
		Bundle bundle = getDefault().getBundle();
		
		//Build zip containing ybootstrap, log4j and MANIFEST.MF
		List<File> sources = new ArrayList<File>();
		sources.add(new File(getPlatformHome().getAbsolutePath() + "/bootstrap/bin/ybootstrap.jar"));
		
		URL url = bundle.getEntry("ybootstrap/log4j-1.2.17.jar");
		File file = null;
		try {
			URL resolvedFileURL = FileLocator.toFileURL(url);
			URI resolvedURI = new URI(resolvedFileURL.getProtocol(), resolvedFileURL.getPath(), null);
			file = new File(resolvedURI);
			sources.add(file);
			
			url = bundle.getEntry("ybootstrap/commons-collections-3.2.2.jar");
			resolvedFileURL = FileLocator.toFileURL(url);
			resolvedURI = new URI(resolvedFileURL.getProtocol(), resolvedFileURL.getPath(), null);
			file = new File(resolvedURI);
			sources.add(file);
			
			url = bundle.getEntry("ybootstrap/META-INF");
			resolvedFileURL = FileLocator.toFileURL(url);
			resolvedURI = new URI(resolvedFileURL.getProtocol(), resolvedFileURL.getPath(), null);
			file = new File(resolvedURI);
			sources.add(file);
			
			ByteArrayOutputStream baos = BundlePackager.buildZip(sources);
			byte[] bytes = baos.toByteArray();
			ByteArrayInputStream bais = new ByteArrayInputStream(bytes);
			
			setYbootstrapBundle(bundle.getBundleContext().installBundle(YBOOTSTRAP_PLUGIN_ID, bais));
			
		}
		catch (URISyntaxException | IOException e) {
			logError("URISyntaxException | IOException", e);
		}
		catch (BundleException e) {
			logError("BundleException", e);
		}
		
	}
	
	public SystemConfig getSystemConfig() {
		if (systemConfig == null) {
			
			Hashtable<String, String> props = loadProperties(getPlatformHome());
			try {
				Field singletonField = SystemConfig.class.getDeclaredField("singleton");
				singletonField.setAccessible(true);
				singletonField.set(this, null);
				Field instanceField = PlatformConfig.class.getDeclaredField("instance");
				instanceField.setAccessible(true);
				instanceField.set(this, null);
			}
			catch (NoSuchFieldException | SecurityException e) {
				logError("NoSuchFieldException | SecurityException", e);
			}
			catch (IllegalArgumentException e) {
				logError("IllegalArgumentException", e);
			}
			catch (IllegalAccessException e) {
				logError("IllegalAccessException", e);
			}
			
			systemConfig = SystemConfig.getInstanceByProps(props);
		}
		return systemConfig;
	}
	
	public void nullifySystemConfig() {
		systemConfig = null;
	}
	
	public PlatformConfig getPlatformConfig() {
		if (platformConfig == null) {
			platformConfig = PlatformConfig.getInstance(getSystemConfig());
		}
		return platformConfig;
	}
	
	public void nullifyPlatformConfig() {
		platformConfig = null;
	}
	
	public YTypeSystem getTypeSystem() {
		if (typeSystem == null) {
			typeSystem = YTypeSystemBuilder.buildTypeSystem();
		}
		return typeSystem;
	}
	
	public void nullifyTypeSystem() {
		typeSystem = null;
	}
	
	public String getConfigDirectory() {
		SystemConfig systemConfig = getPlatformConfig().getSystemConfig();
		if (systemConfig != null) {
			return systemConfig.getConfigDir().getAbsolutePath();
		}
		return null;
	}
	
	public Set<? extends YType> getAllTypes() {
		if (allTypes == null) {
			allTypes = getTypeSystem().getTypes();
		}
		return allTypes;
	}
	
	public void nullifyAllTypes() {
		allTypes = null;
	}
	
	public List<String> getAllTypeNames() {
		if (allTypeNames == null) {
			Set<? extends YType> allTypes = getAllTypes();
			allTypeNames = new ArrayList<String>(allTypes.size());
			for (YType type : allTypes) {
				allTypeNames.add(type.getCode());
			}
		}
		return allTypeNames;
	}
	
	public void nullifyAllTypeNames() {
		allTypeNames = null;
	}
	
	public List<String> getAllAttributeNames(String typeName) {
		Set<YAttributeDescriptor> typeAttributes = getTypeSystem().getAttributes(typeName);
		List<String> allAttributeNames = new ArrayList<String>(typeAttributes.size());
		for (YAttributeDescriptor attribute : typeAttributes) {
			allAttributeNames.add(attribute.getQualifier());
		}
		return allAttributeNames;
	}
	
	public String getAttributeName(String typeName, String potentialAttributeName) {
		YType type = getTypeSystem().getType(typeName);
		YAttributeDescriptor attribute = getTypeSystem().getAttribute(type.getCode(), potentialAttributeName);
		if (attribute != null) {
			return attribute.getQualifier();
		}
		return null;
	}
	
	public String getTypeLoaderInfo(String typeName) {
		YType type = getTypeSystem().getType(typeName);
		if (type != null) {
			return type.getLoaderInfo();//core-items.xml:2793(ItemTypeTagListener)
		}
		return "";
	}
	
	public Set<ExtensionHolder> getAllExtensionsForPlatform() {
		Set<ExtensionHolder> allExtensions = new HashSet<ExtensionHolder>();
		List<ExtensionInfo> allExtensionInfos = getPlatformConfig().getExtensionInfosInBuildOrder();
		for (ExtensionInfo extension : allExtensionInfos) {
			ExtensionHolder extHolder = createExtensionHolderFromExtensionInfo(extension);
			if (extHolder != null) {
				allExtensions.add(extHolder);
			}
		}
		return allExtensions;
	}
	
	private ExtensionHolder createExtensionHolderFromExtensionInfo(ExtensionInfo extension) {
		
		ExtensionHolder extHolder = null;
		if (!extension.isCoreExtension()) {
			String path = extension.getExtensionDirectory().getAbsolutePath();
			extHolder = new ExtensionHolder(path, extension.getName());
			if (extension.getCoreModule() != null) {
				extHolder.setCoreModule(true);
			}
			if (extension.getWebModule() != null) {
				extHolder.setWebModule(true);
			}
			if (extension.getHMCModule() != null && getPlatformConfig().getExtensionInfo("hmc") != null) {
				extHolder.setHmcModule(true);
			}
			
			extHolder.setBackofficeModule(false);
			String backOfficeMeta = extension.getMeta("backoffice-module");
			if (backOfficeMeta != null && backOfficeMeta.equalsIgnoreCase("true")) {
				extHolder.setBackofficeModule(true);
			}
			
			extHolder.setAddOnModule(false);
			File addonDir = new File(path, "acceleratoraddon");
			if (addonDir.exists() && addonDir.isDirectory()) {
				extHolder.setAddOnModule(true);
			}
			
			File libDir = new File(path, "lib");
			if (libDir.exists() && libDir.isDirectory()) {
				File[] files = libDir.listFiles(new FilenameFilter() {
				    public boolean accept(File dir, String name) {
				        return name.toLowerCase(Locale.ENGLISH).endsWith(".jar");
				    }
				});
				for (File file : files) {
					extHolder.getJarFiles().add(file.getName());
				}
			}
			
			if (!extension.getAllRequiredExtensionNames().isEmpty())
			{
				List<String> extensions = new LinkedList(extension.getAllRequiredExtensionNames());
				if (!extensions.contains("platform"))
				{
					extensions.add("platform");
				}
				extHolder.setDependentExtensions(extensions);
			}
			
		}
		
		return extHolder;
	}
	
	private static Hashtable<String, String> loadProperties(File platformHome) {
		
		File file = new File(platformHome, "active-role-env.properties");
		if ( !file.exists() ) {
			file = new File(platformHome, "env.properties");
			if (!file.exists()) {
				throw new IllegalStateException("Could not find either " + platformHome + "/env.properties or " + platformHome + "/active-role-env.properties, ensure you have built the platform before continuing");
			}
		}
		
		Hashtable<String, String> props = new Hashtable<String, String>();
		props.put("platformhome", platformHome.getAbsolutePath());
		Properties properties = new Properties();
		InputStream in = null;
		try {
			in = new FileInputStream(file.getAbsolutePath());
			properties.load(in);
			if (in != null) {
				in.close();
			}
		}
		catch (FileNotFoundException fnfe) {
			throw new IllegalArgumentException("Failed to load the properties for this platform", fnfe);
		}
		catch (IOException ie) {
			throw new IllegalArgumentException("Failed to load the properties for this platform", ie);
		}
		finally
		{
			try {
				if (in != null) {
					in.close();
				}
			} catch (IOException ie) {
				throw new IllegalArgumentException("Failed to close input stream after loading the properties for this platform", ie);
			}
		}
		
		for (Entry<?, ?> prop : properties.entrySet()) {
			
			String a = prop.getKey().toString();
			String b = prop.getValue().toString();
			String c = platformHome.getAbsolutePath();
			
			props.put(a, StringUtils.replace(b, "${platformhome}", c));
		}
		
		// hybris 5.7 additional properties
        props.put("HYBRIS_ROLES_DIR", platformHome.getAbsolutePath() + "/../../roles");
        props.put("HYBRIS_BOOTSTRAP_BIN_DIR", platformHome.getAbsolutePath() + "/bootstrap/bin");
        
		return props;
	}
	
	public static void log(String msg) {
		getDefault().log(msg, null);
	}
	
	public static void logError(String msg, Exception e) {
		getDefault().log(msg, e);
	}

	public void log(String msg, Exception e) {
		Status status = null;
		if (e != null) {
			status = new Status(Status.ERROR, Activator.PLUGIN_ID, Status.ERROR, msg, e);
		}
		else {
			status = new Status(Status.INFO, Activator.PLUGIN_ID, Status.OK, msg, e);
		}
		getLog().log(status);
	}
	
}
