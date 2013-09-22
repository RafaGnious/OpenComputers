package li.cil.oc.common.tileentity

import li.cil.oc.api.{INetworkNode, ComponentType}
import li.cil.oc.common.computer.IComputer
import li.cil.oc.server.computer.Drivers
import net.minecraft.inventory.IInventory
import net.minecraft.item.ItemStack
import net.minecraft.nbt.NBTTagCompound
import net.minecraft.nbt.NBTTagList
import net.minecraft.world.World

trait ItemComponentProxy extends IInventory with INetworkNode {
  protected val inventory = new Array[ItemStack](8)

  protected val itemComponents = Array.fill(inventory.length)(0)

  protected val computer: IComputer

  def world: World

  def itemDriver(id: Int) = itemComponents.indexOf(id) match {
    case -1 => None
    case slot => Drivers.driverFor(inventory(slot))
  }

  def itemNode(id: Int) = itemComponents.indexOf(id) match {
    case -1 => None
    case slot => Drivers.driverFor(inventory(slot)) match {
      case None => None
      case Some(driver) => driver.instance.node(inventory(slot))
    }
  }

  def readItemsFromNBT(nbt: NBTTagCompound) = {
    val list = nbt.getTagList("list")
    for (i <- 0 until list.tagCount) {
      val slotNbt = list.tagAt(i).asInstanceOf[NBTTagCompound]
      val slot = slotNbt.getByte("slot")
      if (slot >= 0 && slot < inventory.length) {
        inventory(slot) = ItemStack.loadItemStackFromNBT(
          slotNbt.getCompoundTag("item"))
        itemComponents(slot) = slotNbt.getInteger("id")
      }
    }
  }

  def writeItemsToNBT(nbt: NBTTagCompound) = {
    val list = new NBTTagList
    inventory.zipWithIndex.filter {
      case (stack, slot) => stack != null
    }.
      foreach {
      case (stack, slot) => {
        val slotNbt = new NBTTagCompound
        slotNbt.setByte("slot", slot.toByte)
        val itemNbt = new NBTTagCompound
        stack.writeToNBT(itemNbt)
        slotNbt.setCompoundTag("item", itemNbt)
        slotNbt.setInteger("id", itemComponents(slot))
        list.appendTag(slotNbt)
      }
    }
    nbt.setTag("list", list)
  }

  // ----------------------------------------------------------------------- //
  // NetworkNode
  // ----------------------------------------------------------------------- //

  override def onConnect() = {
    super.onConnect()
    for (i <- 0 until inventory.length) {
      itemNode(i) match {
        case None => // Ignore.
        case Some(node) => network.connect(this, node)
      }
    }
  }

  override def onDisconnect() = {
    super.onDisconnect()
    for (i <- 0 until inventory.length) {
      itemNode(i) match {
        case None => // Ignore.
        case Some(node) => node.network.remove(node)
      }
    }
  }

  // ----------------------------------------------------------------------- //
  // IInventory
  // ----------------------------------------------------------------------- //

  def getInventoryStackLimit = 1

  def getInvName = "oc.container.computer"

  def getSizeInventory = inventory.length

  def getStackInSlot(i: Int) = inventory(i)

  def decrStackSize(slot: Int, amount: Int) = {
    val stack = getStackInSlot(slot)
    if (stack == null)
      null
    else if (stack.stackSize <= amount) {
      setInventorySlotContents(slot, null)
      stack
    }
    else {
      val subStack = stack.splitStack(amount)
      if (stack.stackSize == 0) {
        setInventorySlotContents(slot, null)
      }
      subStack
    }
  }

  def getStackInSlotOnClosing(slot: Int) = null

  def setInventorySlotContents(slot: Int, item: ItemStack) = {
    // Uninstall component previously in that slot.
    if (!world.isRemote) itemNode(slot) match {
      case Some(node) => node.network.remove(node)
      case _ => // Nothing to do.
    }
    itemComponents(slot) = 0

    inventory(slot) = item
    if (item != null && item.stackSize > getInventoryStackLimit)
      item.stackSize = getInventoryStackLimit

    if (!world.isRemote && inventory(slot) != null) {
      Drivers.driverFor(inventory(slot)) match {
        case None => // Nothing to do, but avoid match errors.
        case Some(driver) => {
          driver.instance.node(inventory(slot)) match {
            case None => // Ignore.
            case Some(node) => network.connect(this, node)
          }
        }
      }
    }
  }

  def isItemValidForSlot(slot: Int, item: ItemStack) = (slot, Drivers.driverFor(item)) match {
    case (_, None) => false // Invalid item.
    case (0, Some(driver)) => driver.instance.componentType(item) == ComponentType.PSU
    case (1 | 2 | 3, Some(driver)) => driver.instance.componentType(item) == ComponentType.PCI
    case (4 | 5, Some(driver)) => driver.instance.componentType(item) == ComponentType.RAM
    case (6 | 7, Some(driver)) => driver.instance.componentType(item) == ComponentType.HDD
    case (_, Some(_)) => false // Invalid slot.
  }

  def isInvNameLocalized = false

  def openChest() {}

  def closeChest() {}
}