package li.cil.oc.common.tileentity

import java.util.concurrent.atomic.AtomicBoolean
import li.cil.oc.api.INetworkMessage
import li.cil.oc.client.computer.{Computer => ClientComputer}
import li.cil.oc.client.{PacketSender => ClientPacketSender}
import li.cil.oc.server.computer.IComputerEnvironment
import li.cil.oc.server.computer.{Computer => ServerComputer}
import li.cil.oc.server.{PacketSender => ServerPacketSender}
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.nbt.NBTTagCompound

class TileEntityComputer(isClient: Boolean) extends TileEntityRotatable with IComputerEnvironment with ItemComponentProxy {
  def this() = this(false)

  protected val computer =
    if (isClient) new ClientComputer(this)
    else new ServerComputer(this)

  private val hasChanged = new AtomicBoolean(true)

  private var isRunning = computer.isRunning

  // ----------------------------------------------------------------------- //
  // NetworkNode
  // ----------------------------------------------------------------------- //

  override def receive(message: INetworkMessage) = message.data match {
    case Array() if message.name == "network.connect" =>
      computer.signal("component_added", message.source.address)
    case Array() if message.name == "network.disconnect" =>
      computer.signal("component_removed", message.source.address)
    case Array(oldAddress: Integer) if message.name == "network.reconnect" =>
      computer.signal("component_updated", message.source.address, oldAddress)
    case Array(name: String, args@_*) if message.name == "signal" =>
      computer.signal(name, args: _*)
    case _ => // Ignore message.
  }

  override def onAddressChange() = computer.signal("address_change", address)

  // ----------------------------------------------------------------------- //
  // General
  // ----------------------------------------------------------------------- //

  def turnOn() = computer.start()

  def turnOff() = computer.stop()

  def isOn = computer.isRunning

  def isOn_=(value: Boolean) = {
    computer.asInstanceOf[ClientComputer].isRunning = value
    worldObj.markBlockForRenderUpdate(xCoord, yCoord, zCoord)
    this
  }

  override def readFromNBT(nbt: NBTTagCompound) = {
    super.readFromNBT(nbt)
    computer.readFromNBT(nbt.getCompoundTag("computer"))
    readItemsFromNBT(nbt.getCompoundTag("items"))
  }

  override def writeToNBT(nbt: NBTTagCompound) = {
    super.writeToNBT(nbt)

    val computerNbt = new NBTTagCompound
    computer.writeToNBT(computerNbt)
    nbt.setCompoundTag("computer", computerNbt)

    val itemsNbt = new NBTTagCompound
    writeItemsToNBT(itemsNbt)
    nbt.setCompoundTag("items", itemsNbt)
  }

  override def updateEntity() = {
    computer.update()
    if (hasChanged.get) {
      worldObj.updateTileEntityChunkAndDoNothing(
        xCoord, yCoord, zCoord, this)
      if (isRunning != computer.isRunning) {
        isRunning = computer.isRunning
        ServerPacketSender.sendComputerState(this, isRunning)
      }
    }
  }

  override def validate() = {
    super.validate()
    if (worldObj.isRemote)
      ClientPacketSender.sendComputerStateRequest(this)
  }

  // ----------------------------------------------------------------------- //
  // Interfaces and updating
  // ----------------------------------------------------------------------- //

  override def isUseableByPlayer(player: EntityPlayer) =
    world.getBlockTileEntity(xCoord, yCoord, zCoord) == this &&
      player.getDistanceSq(xCoord + 0.5, yCoord + 0.5, zCoord + 0.5) < 64

  override def world = worldObj

  override def markAsChanged() = hasChanged.set(true)
}