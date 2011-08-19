jna.jar was custom built from git to test jna fixes for memory finalization 
crashes on Windows. This build does seem to not have the problem and is likely 
fixed. These changes are expected to be present in the next jna release 
(perhaps 3.3.1, but the version number hasn't been selected just yet).

To build it, run netbeans from an ossbuild env command prompt, open the 
project, right-click the project name, and select "build."

