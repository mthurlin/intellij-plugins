package com.intellij.flex.build;

import com.intellij.flex.FlexCommonBundle;
import com.intellij.flex.FlexCommonUtils;
import com.intellij.flex.model.bc.JpsFlexBCDependencyEntry;
import com.intellij.flex.model.bc.JpsFlexBuildConfiguration;
import com.intellij.flex.model.bc.JpsFlexDependencyEntry;
import com.intellij.flex.model.run.JpsBCBasedRunnerParameters;
import com.intellij.flex.model.run.JpsFlashRunConfigurationType;
import com.intellij.flex.model.run.JpsFlexUnitRunConfigurationType;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.PathUtilRt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.builders.BuildRootDescriptor;
import org.jetbrains.jps.builders.BuildRootIndex;
import org.jetbrains.jps.builders.BuildTarget;
import org.jetbrains.jps.builders.storage.BuildDataPaths;
import org.jetbrains.jps.indices.IgnoredFileIndex;
import org.jetbrains.jps.indices.ModuleExcludeIndex;
import org.jetbrains.jps.model.JpsModel;
import org.jetbrains.jps.model.JpsSimpleElement;
import org.jetbrains.jps.model.java.JavaSourceRootProperties;
import org.jetbrains.jps.model.java.JavaSourceRootType;
import org.jetbrains.jps.model.module.JpsTypedModuleSourceRoot;
import org.jetbrains.jps.model.runConfiguration.JpsRunConfigurationType;
import org.jetbrains.jps.util.JpsPathUtil;

import java.io.File;
import java.io.FileFilter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class FlexBuildTarget extends BuildTarget<BuildRootDescriptor> {
  private final @NotNull JpsFlexBuildConfiguration myBC;
  private final @NotNull String myId;
  private final @Nullable JpsRunConfigurationType<? extends JpsBCBasedRunnerParameters<?>> myRunConfigType;
  private final @Nullable String myRunConfigName;

  /**
   * @param forcedDebugStatus <code>true</code> or <code>false</code> means that this bc is compiled for further packaging and we need swf to have corresponding debug status;
   *                          <code>null</code> means that bc is compiled as is (i.e. as configured) without any modifications
   */
  public FlexBuildTarget(final @NotNull JpsFlexBuildConfiguration bc, final @Nullable Boolean forcedDebugStatus) {
    super(FlexBuildTargetType.INSTANCE);

    myBC = bc;
    myRunConfigType = null;
    myRunConfigName = null;
    myId = FlexCommonUtils.getBuildTargetId(myBC.getModule().getName(), myBC.getName(), forcedDebugStatus);
  }

  public FlexBuildTarget(final @NotNull JpsFlexBuildConfiguration bc,
                         final @NotNull JpsRunConfigurationType<? extends JpsBCBasedRunnerParameters<?>> runConfigType,
                         final @NotNull String runConfigName) {
    super(FlexBuildTargetType.INSTANCE);

    myBC = bc;
    myRunConfigType = runConfigType;
    myRunConfigName = runConfigName;

    assert runConfigType instanceof JpsFlashRunConfigurationType ||
           runConfigType instanceof JpsFlexUnitRunConfigurationType : runConfigType;

    final String runConfigTypeId = runConfigType instanceof JpsFlashRunConfigurationType ? JpsFlashRunConfigurationType.ID
                                                                                         : JpsFlexUnitRunConfigurationType.ID;
    myId = FlexCommonUtils.getBuildTargetIdForRunConfig(runConfigTypeId, runConfigName);
  }

  @NotNull
  public String getId() {
    return myId;
  }

  public Collection<BuildTarget<?>> computeDependencies() {
    final Collection<BuildTarget<?>> result = new ArrayList<BuildTarget<?>>();

    for (JpsFlexDependencyEntry entry : myBC.getDependencies().getEntries()) {
      if (entry instanceof JpsFlexBCDependencyEntry) {
        final JpsFlexBuildConfiguration dependencyBC = ((JpsFlexBCDependencyEntry)entry).getBC();
        if (dependencyBC != null) {
          result.add(new FlexBuildTarget(dependencyBC, null));
        }
      }
    }

    return result;
  }

  @NotNull
  public List<BuildRootDescriptor> computeRootDescriptors(final JpsModel model,
                                                          final ModuleExcludeIndex index,
                                                          final IgnoredFileIndex ignoredFileIndex,
                                                          final BuildDataPaths dataPaths) {
    final List<BuildRootDescriptor> roots = new ArrayList<BuildRootDescriptor>();

    for (JpsTypedModuleSourceRoot<JpsSimpleElement<JavaSourceRootProperties>> sourceRoot
      : myBC.getModule().getSourceRoots(JavaSourceRootType.SOURCE)) {

      final File root = JpsPathUtil.urlToFile(sourceRoot.getUrl());
      roots.add(new FlexSourceRootDescriptor(root));
    }

    return roots;
  }

  @Nullable
  public BuildRootDescriptor findRootDescriptor(final String rootId, final BuildRootIndex rootIndex) {
    for (BuildRootDescriptor descriptor : rootIndex.getTargetRoots(this, null)) {
      if (descriptor.getRootId().equals(rootId)) {
        return descriptor;
      }
    }

    return null;
  }

  @NotNull
  public String getPresentableName() {
    return FlexCommonBundle.message("bc.0.module.1", myBC.getName(), myBC.getModule().getName());
  }

  @Nullable
  public File getOutputDir(final BuildDataPaths paths) {
    return new File(PathUtilRt.getParentPath(myBC.getActualOutputFilePath()));
  }


  public String toString() {
    return myId;
  }

  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    final FlexBuildTarget target = (FlexBuildTarget)o;

    if (!myId.equals(target.myId)) return false;

    return true;
  }

  public int hashCode() {
    return myId.hashCode();
  }


  private class FlexSourceRootDescriptor extends BuildRootDescriptor {
    private final File myRoot;

    public FlexSourceRootDescriptor(File root) {
      myRoot = root;
    }

    @Override
    public String getRootId() {
      return FileUtil.toSystemIndependentName(myRoot.getAbsolutePath());
    }

    @Override
    public File getRootFile() {
      return myRoot;
    }

    @Override
    public BuildTarget<?> getTarget() {
      return FlexBuildTarget.this;
    }

    @Override
    public FileFilter createFileFilter() {
      return new FileFilter() {
        @Override
        public boolean accept(File pathname) {
          return true;
        }
      };
    }
  }
}
