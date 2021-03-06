package com.sevenwatt.picos.config;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;

import org.eclipse.cdt.core.CCorePlugin;
import org.eclipse.cdt.core.cdtvariables.ICdtVariable;
import org.eclipse.cdt.core.cdtvariables.ICdtVariableManager;
import org.eclipse.cdt.core.cdtvariables.IStorableCdtVariables;
import org.eclipse.cdt.core.cdtvariables.IUserVarSupplier;
import org.eclipse.cdt.core.envvar.IEnvironmentVariable;
import org.eclipse.cdt.internal.core.envvar.EnvironmentVariableManager;
import org.eclipse.cdt.internal.core.envvar.UserDefinedEnvironmentSupplier;
import org.eclipse.cdt.utils.envvar.StorableEnvironment;
import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Platform;
import org.eclipse.jface.window.Window;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.handlers.HandlerUtil;
import org.eclipse.debug.core.DebugPlugin;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.debug.core.ILaunchConfigurationType;
import org.eclipse.debug.core.ILaunchConfigurationWorkingCopy;
import org.eclipse.debug.core.ILaunchManager;

/**
 * ConfigHandler extends AbstractHandler, an IHandler base class.
 * 
 * @see org.eclipse.core.commands.IHandler
 * @see org.eclipse.core.commands.AbstractHandler
 */
public class ConfigHandler extends AbstractHandler {
	private final static IProgressMonitor NULL_MONITOR = new NullProgressMonitor();

	private static final String SEPARATOR = System.getProperty(
			"path.separator", ";"); //$NON-NLS-1$ //$NON-NLS-2$
	private static final String DIRSEP = "/"; //$NON-NLS-1$
	private static final UserDefinedEnvironmentSupplier fUserSupplier = EnvironmentVariableManager.fUserSupplier;

	//private static final ICdtVariableManager vmgr = CCorePlugin.getDefault().getCdtVariableManager();
	private static final IUserVarSupplier fUserSup = CCorePlugin.getUserVarSupplier();	
	
	private String fastSearchPaths = Messages.ConfigHandler_fastSearch;
	private String searchPaths = Messages.ConfigHandler_Search;
	// private String defPatternMake = "C:/*/GNU ARM Eclipse/Build Tools/bin";
	// private String defPatternUtils = "C:/*/GnuWin32/bin";
	// private String defPatternChain = "C:/*/GNU Tools ARM Embedded/*/bin";
	private String defPatternMake = Messages.ConfigHandler_MakePattern;
	private String defPatternUtils = Messages.ConfigHandler_UtilsPattern;
	private String defPatternChain = Messages.ConfigHandler_ChainPattern;
	private String defPathMake = Messages.ConfigHandler_DefaultMake;
	private String defPathUtils = Messages.ConfigHandler_DefaultUtils;
	private String defPathChain = Messages.ConfigHandler_DefaultChain;
	private boolean defMakeExist = false;
	private boolean defUtilsExist = false;
	private boolean defChainExist = false;

	/**
	 * The constructor.
	 */
	public ConfigHandler() {
		if (Platform.getOS().equals(Platform.OS_WIN32)) {
			ArrayList<String> chains = new ArrayList<String>();
			ArrayList<String> makes = new ArrayList<String>();
			ArrayList<String> utils = new ArrayList<String>();
			String[] sPaths = { fastSearchPaths, searchPaths };
			for (String sPath : sPaths) {
				for (String bPath : sPath.split(SEPARATOR)) {
					try {
						PathMatcher chainMatcher = FileSystems
								.getDefault()
								.getPathMatcher(
										"glob:"	+ defPatternChain + DIRSEP + Messages.ConfigHandler_Compiler_exe); //$NON-NLS-1$
						PathMatcher makeMatcher = FileSystems
								.getDefault()
								.getPathMatcher(
										"glob:"	+ defPatternMake + DIRSEP + Messages.ConfigHandler_Make_exe); //$NON-NLS-1$
						PathMatcher utilsMatcher = FileSystems
								.getDefault()
								.getPathMatcher(
										"glob:"	+ defPatternUtils + DIRSEP + Messages.ConfigHandler_Mkdir_exe); //$NON-NLS-1$
						Files.walkFileTree(Paths.get(bPath),
								new SimpleFileVisitor<Path>() {
							@Override
							public FileVisitResult visitFile(Path file,
									BasicFileAttributes attrs)
											throws IOException {
								if (chainMatcher.matches(file)) {
									chains.add(file.getParent()
											.toString());
								}
								if (makeMatcher.matches(file)) {
									makes.add(file.getParent()
											.toString());
								}
								if (utilsMatcher.matches(file)) {
									utils.add(file.getParent()
											.toString());
								}
								return FileVisitResult.CONTINUE;
							}

							@Override
							public FileVisitResult visitFileFailed(
									Path file, IOException exc)
											throws IOException {
								return FileVisitResult.CONTINUE;
							}
						});
					} catch (IOException e) {
					}
				}
				// Step out if search on fast paths yielded result
				if (chains.size() > 0 && makes.size() > 0 && utils.size() > 0)
					break;
			}
			// if (chains.size()>1)
			// chains.sort(null);
			if (chains.size() > 0) {
				defPathChain = chains.get(chains.size() - 1);
				defChainExist = true;
			}
			// if (makes.size()>1)
			// makes.sort(null);
			if (makes.size() > 0) {
				defPathMake = makes.get(makes.size() - 1);
				defMakeExist = true;
			}
			// if (utils.size()>1)
			// utils.sort(null);
			if (utils.size() > 0) {
				defPathUtils = utils.get(utils.size() - 1);
				defUtilsExist = true;
			}
		}

		// Common actions
		// create generic (no c++ nature) project for shared resources
		try {
			createGenericProject(Messages.ConfigHandler_SharedProject);
		} catch (CoreException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		//create external tools
		try {
			createOpenOCDLaunch(Messages.ConfigHandler_LPC810, true);
			createOpenOCDLaunch(Messages.ConfigHandler_LPC812, false);
			createOpenOCDLaunch(Messages.ConfigHandler_LPC824, true);
		} catch (CoreException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	/**
	 * the command has been executed, so extract extract the needed information
	 * from the application context.
	 */
	public Object execute(ExecutionEvent event) throws ExecutionException {
		IWorkbenchWindow window = HandlerUtil
				.getActiveWorkbenchWindowChecked(event);
		ConfigDialog cfgDialog = new ConfigDialog(window.getShell());

		// KEEP: As code example.
		// // Update ENV vars within a project.
		// // config=null updates workspace variables, but fails to store them
		// persistently.
		// IContributedEnvironment environment =
		// CCorePlugin.getDefault().getBuildEnvironmentManager().getContributedEnvironment();
		// IProject project =
		// CCorePlugin.getWorkspace().getRoot().getProject("embello");
		// ICProjectDescription projectDescription =
		// CoreModel.getDefault().getProjectDescription(project, true);
		// ICConfigurationDescription config =
		// projectDescription.getActiveConfiguration();
		// environment.addVariable("PICOS_BUILDTOOL", cfgDialog.getBuildTools()
		// , IEnvironmentVariable.ENVVAR_APPEND, null, config);
		// environment.addVariable("PICOS_TOOLCHAIN", cfgDialog.getToolchain() ,
		// IEnvironmentVariable.ENVVAR_APPEND, null, config);
		// // Update project (description)
		// CoreModel.getDefault().setProjectDescription(project,
		// projectDescription);

		// NOTE: No public interfaces available to persistenly store workspace
		// ENV vars. See:
		// http://stackoverflow.com/questions/6186754/eclipse-cdt-how-to-save-project-wide-environment-variables-programmatically-pe
		// https://www.eclipse.org/forums/index.php/t/812519/
		UserDefinedEnvironmentSupplier fUserSupplier = EnvironmentVariableManager.fUserSupplier;
		StorableEnvironment environment = fUserSupplier
				.getWorkspaceEnvironmentCopy();
		
		IStorableCdtVariables prefvars = fUserSup.getWorkspaceVariablesCopy();
		
		IEnvironmentVariable envvar = environment
				.getVariable("PICOS_BUILDTOOL"); //$NON-NLS-1$
		if (envvar != null)
			cfgDialog.setBuildTools(envvar.getValue());
		else if (defMakeExist)
			cfgDialog.setBuildTools(defPathMake);
		envvar = environment.getVariable("PICOS_TOOLCHAIN"); //$NON-NLS-1$
		if (envvar != null)
			cfgDialog.setToolchain(envvar.getValue());
		else if (defChainExist)
			cfgDialog.setToolchain(defPathChain);
		envvar = environment.getVariable("PICOS_GNU_UTILS"); //$NON-NLS-1$
		if (envvar != null)
			cfgDialog.setGNUUtils(envvar.getValue());
		else if (defUtilsExist)
			cfgDialog.setGNUUtils(defPathUtils);
		cfgDialog.create();
		if (cfgDialog.open() == Window.OK) {
			// Add variable to workspace
			prefvars.createMacro("picosToolchain", ICdtVariable.VALUE_PATH_DIR, cfgDialog.getToolchain());
			prefvars.createMacro("picosStdInc", ICdtVariable.VALUE_PATH_DIR, cfgDialog.getToolchain()+Messages.ConfigHandler_Std_Includes);
			prefvars.createMacro("picosBuildtool", ICdtVariable.VALUE_PATH_DIR, cfgDialog.getBuildTools());
			prefvars.createMacro("picosGnuUtils", ICdtVariable.VALUE_PATH_DIR, cfgDialog.getGNUUtils());
			try {
				fUserSup.setWorkspaceVariables(prefvars);
			} catch (CoreException e) {
				e.printStackTrace();
			}
			
			//environment.createVariable("PICOS_BUILDTOOL", cfgDialog.getBuildTools()); //$NON-NLS-1$
			//environment.createVariable("PICOS_TOOLCHAIN", cfgDialog.getToolchain()); //$NON-NLS-1$
			//environment.createVariable("PICOS_GNU_UTILS", cfgDialog.getGNUUtils()); //$NON-NLS-1$
			//environment.createVariable("PICOS_STDINCLUDE", cfgDialog.getToolchain()+Messages.ConfigHandler_Std_Includes); //$NON-NLS-1$
			//String path = "${PICOS_BUILDTOOL}"+ SEPARATOR + "${PICOS_TOOLCHAIN}"+ SEPARATOR + "${PICOS_GNU_UTILS}"; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
			String path = "${picosBuildtool}"+ SEPARATOR + "${picosToolchain}"+ SEPARATOR + "${picosGnuUtils}"; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
			environment.createVariable("PATH", path); //$NON-NLS-1$
			// //KEEP: Only with ENVVAR_APPEND, %PATH% gets expanded. Kept as
			// example.
			// environment.createVariable("PATH", "${PICOS_BUILDTOOL};%PATH%",
			// IEnvironmentVariable.ENVVAR_APPEND, SEPARATOR);
			fUserSupplier.setWorkspaceEnvironment(environment);
		}
		return null;
	}

	private void findTools() {

	}

	private IProject createGenericProject(String projectName)
			throws CoreException {
		IWorkspaceRoot root = ResourcesPlugin.getWorkspace().getRoot();
		IProject project = root.getProject(projectName);
		if (!project.exists()) {
			project.create(NULL_MONITOR);
		} else {
			project.refreshLocal(IResource.DEPTH_INFINITE, null);
		}

		if (!project.isOpen())
			project.open(NULL_MONITOR);

		return project;
	}

	private void createOpenOCDLaunch(String chip, boolean favo) throws CoreException {
		String toolName = Messages.ConfigHandler_ToolNamePre+chip;
		String toolCmdOpt = Messages.ConfigHandler_CmdOptPre + 
				chip.toLowerCase() + Messages.ConfigHandler_CmdOptPost;
		ILaunchManager manager = DebugPlugin.getDefault().getLaunchManager();
		ILaunchConfigurationType launchConfigurationType = manager
				.getLaunchConfigurationType("org.eclipse.ui.externaltools.ProgramLaunchConfigurationType"); //$NON-NLS-1$
		ILaunchConfiguration[] configurations = manager.getLaunchConfigurations(launchConfigurationType);
		for (int i = 0; i < configurations.length; i++) {
			ILaunchConfiguration configuration = configurations[i];
			if (configuration.getName().equals(toolName)) {
				configuration.delete();
				break;
			}
		}
		ILaunchConfigurationWorkingCopy workingCopy = launchConfigurationType
				.newInstance(null, toolName);

		IContainer container = getContainer(Messages.ConfigHandler_SharedProject);
		workingCopy.setContainer(container);
		
		if (favo) {
			ArrayList<String> listValue = new ArrayList<String>();
			listValue.add(new String("org.eclipse.ui.externaltools.launchGroup")); //$NON-NLS-1$
			workingCopy.setAttribute("org.eclipse.debug.ui.favoriteGroups", //$NON-NLS-1$
					listValue);
		}
		
		workingCopy.setAttribute("org.eclipse.ui.externaltools.ATTR_LAUNCH_CONFIGURATION_BUILD_SCOPE", //$NON-NLS-1$
				"${none}"); //$NON-NLS-1$
		workingCopy.setAttribute("org.eclipse.ui.externaltools.ATTR_LOCATION", //$NON-NLS-1$
				Messages.ConfigHandler_OpenOCDPath);
		workingCopy.setAttribute("org.eclipse.ui.externaltools.ATTR_TOOL_ARGUMENTS", //$NON-NLS-1$
				 toolCmdOpt);
		ILaunchConfiguration configuration = workingCopy.doSave();
		// DebugUITools.launch(configuration, ILaunchManager.RUN_MODE);
	}

	private IContainer getContainer(String path) {
		org.eclipse.core.runtime.Path containerPath = new org.eclipse.core.runtime.Path(path);
		return (IContainer) ResourcesPlugin.getWorkspace().getRoot().findMember(containerPath);
	}

}

