org.eclipse.cdt.embsysregview

=============================
This is a personal work clone of the great EmbSysRegView project.
The official page of the project is at [SourceForge](http://embsysregview.sourceforge.net/).

This exists mainly so I can try out some UI improvements and new features.
I'm not sure about the maintenance status of the official project, I've [posted a patch](https://sourceforge.net/p/embsysregview/discussion/964553/thread/5b1f63c9/) but so far had little response.
At the time this was first published (mid-September 2016) it's been more than a month without any response to that patch.

Screenshots
===========
Here's how EmbSysRegView looked before my changes:

![EmbSysRegView before changes](https://raw.githubusercontent.com/unwind/embsysregview/master/org.eclipse.cdt.embsysregview_website/htdocs/img/unwind-before.png)

and here's how it looks with my changes:

![EmbSysRegView after changes](https://raw.githubusercontent.com/unwind/embsysregview/master/org.eclipse.cdt.embsysregview_website/htdocs/img/unwind-after.png)

Staggering differences, I know.

The new (tiny) buttons in the top-left corner are:

- Collapse all

 This simply collapses all tree nodes, minimizing the amount of visible information.
 Not super-critical, but sometimes handy when you want to get back to an overview of the registers.

- Activate/deactivate all

 This activates ("turns green") all registers, causing their state to be updated when you single-step.
 If you click this with the <kbd>Control</kbd> key held down, it will instead deactivate ("turn black") all registers.
 Operating on all registers like this is a bit blunt, but sometimes useful, especially with the copy to clipboard button.

- Copy active registers to clipboard

 This copies the name and value of all active (green) registers to the system clipboard, as text.
 It will look something like this:

    REG_A=0
    REG_B=0

 and so on, for made-up register names `REG_A` and `REG_B`.
 Note that only active registers are copied, since EmbSysRegView doesn't know the value of non-green registers.
