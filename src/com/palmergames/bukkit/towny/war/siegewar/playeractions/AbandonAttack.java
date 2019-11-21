package com.palmergames.bukkit.towny.war.siegewar.playeractions;

import com.palmergames.bukkit.towny.TownyMessaging;
import com.palmergames.bukkit.towny.TownySettings;
import com.palmergames.bukkit.towny.exceptions.TownyException;
import com.palmergames.bukkit.towny.object.*;
import com.palmergames.bukkit.towny.permissions.PermissionNodes;
import com.palmergames.bukkit.towny.war.siegewar.utils.SiegeWarDbUtil;
import com.palmergames.bukkit.towny.war.siegewar.enums.SiegeStatus;
import com.palmergames.bukkit.towny.war.siegewar.locations.SiegeZone;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockPlaceEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Goosius
 */
public class AbandonAttack {

    public static void processAbandonSiegeRequest(Player player,
                                                  Block block,
                                                  List<TownBlock> nearbyTownBlocksWithTowns,
                                                  BlockPlaceEvent event)  {
        try {

            if(!TownySettings.getWarSiegeAbandonEnabled())
                throw new TownyException("Siege abandon not allowed");

            Resident resident = TownyUniverse.getDataSource().getResident(player.getName());
            if(!resident.hasTown())
                throw new TownyException("You must belong to a town to abandon a siege");

            Town townOfResident = resident.getTown();
            if(!townOfResident.hasNation())
                throw new TownyException("You must belong to a nation to abandon a siege");

            //If player has no permission to abandon,send error
            if (!TownyUniverse.getPermissionSource().testPermission(player, PermissionNodes.TOWNY_COMMAND_NATION_SIEGE_ABANDON.getNode()))
                throw new TownyException(TownySettings.getLangString("msg_err_command_disable"));

            //Get list of adjacent towns with sieges
            List<Town> nearbyTownsWithSieges =new ArrayList<>();
            for(TownBlock nearbyTownBlock: nearbyTownBlocksWithTowns) {
                if(nearbyTownBlock.getTown().hasSiege()
                        && nearbyTownBlock.getTown().getSiege().getStatus() == SiegeStatus.IN_PROGRESS){
                    nearbyTownsWithSieges.add(nearbyTownBlock.getTown());
                }
            }

            //If none are under active siege, send error
            if(nearbyTownsWithSieges.size() == 0)
                throw new TownyException("You cannot place an abandon banner because none of the nearby towns are under siege.");

            //Get the active siege zones
            List<SiegeZone> nearbyActiveSiegeZones = new ArrayList<>();
            for(Town nearbyTownWithSiege: nearbyTownsWithSieges) {
                for(SiegeZone siegeZone: nearbyTownWithSiege.getSiege().getSiegeZones().values()) {
                    if(siegeZone.isActive())
                        nearbyActiveSiegeZones.add(siegeZone);
                }
            }

            //Find the nearest active zone to the player
            SiegeZone targetedSiegeZone = null;
            double distanceToTarget = -1;
            for(SiegeZone siegeZone: nearbyActiveSiegeZones) {
                if (targetedSiegeZone == null) {
                    targetedSiegeZone = siegeZone;
                    distanceToTarget = block.getLocation().distance(targetedSiegeZone.getFlagLocation());
                } else {
                    double distanceToNewTarget = block.getLocation().distance(siegeZone.getFlagLocation());
                    if(distanceToNewTarget < distanceToTarget) {
                        targetedSiegeZone = siegeZone;
                        distanceToTarget = distanceToNewTarget;
                    }
                }
            }

            //If the player's nation is not the attacker, send error
            Nation nationOfResident = townOfResident.getNation();
            if(targetedSiegeZone.getAttackingNation() != nationOfResident)
                throw new TownyException("Your nation is not attacking this town right now");

            //If the player is too far from the targeted zone, error error
            if(distanceToTarget > TownySettings.getTownBlockSize())
                throw new TownyException("You cannot place an abandon banner because " +
                        "you are too far from the nearest attack banner. " +
                        "Move closer to the attack banner");

            attackerAbandon(targetedSiegeZone);

        } catch (TownyException x) {
            TownyMessaging.sendErrorMsg(player, x.getMessage());
            event.setCancelled(true);
        }
    }

    private static void attackerAbandon(SiegeZone siegeZone) {
        siegeZone.setActive(false);
        TownyUniverse.getDataSource().saveSiegeZone(siegeZone);

        TownyMessaging.sendGlobalMessage(siegeZone.getAttackingNation().getName() + " has abandoned their attack on" + siegeZone.getDefendingTown().getName());

        if (siegeZone.getSiege().getActiveAttackers().size() == 0) {
            SiegeWarDbUtil.updateAndSaveSiegeCompletionValues(siegeZone.getSiege(),
                    SiegeStatus.ATTACKER_ABANDON,
                    null);
            TownyMessaging.sendGlobalMessage("The siege on " + siegeZone.getDefendingTown().getName() +" has been abandoned all attackers.");
        }
    }
}
