package net.sourceforge.opencamera;

import org.junit.experimental.categories.Categories;
import org.junit.runner.RunWith;
import org.junit.runners.Suite;

/** Small set of tests to run on very old devices.
 */

@RunWith(Categories.class)
@Categories.IncludeCategory(net.sourceforge.opencamera.OldDeviceTests.class)
@Suite.SuiteClasses({net.sourceforge.opencamera.InstrumentedTest.class})
public class OldDeviceInstrumentedTests {}
